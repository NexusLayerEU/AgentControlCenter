package eu.nexuslayer.acc.hooks;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.config.AccProperties;
import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentEvent;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.util.Json;

/**
 * Reads what was actually said — the prompts you typed and the model's replies —
 * out of Claude Code's own transcript.
 *
 * <p>Hooks give ACC the tool activity of a session it did not launch, but nothing
 * about the conversation driving it. Every hook payload carries a
 * {@code transcript_path} to a JSONL file holding the whole exchange, so this
 * tails that file and fills in the missing half.
 *
 * <p>Only prompts, assistant text and thinking are taken. {@code tool_use} and
 * {@code tool_result} blocks are deliberately skipped: the hooks already record
 * those, with better timing information than the transcript carries.
 *
 * <p>Progress is stored as a byte offset per Claude session, so a daemon restart
 * resumes mid-file instead of replaying the conversation.
 */
@Service
public class TranscriptReader {

    private static final Logger log = LoggerFactory.getLogger(TranscriptReader.class);

    private final JdbcTemplate jdbc;
    private final EventService events;
    private final AccProperties properties;

    /** One reader per session: hooks arrive concurrently and must not double-read. */
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService delayed = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "acc-transcript");
        t.setDaemon(true);
        return t;
    });

    public TranscriptReader(JdbcTemplate jdbc, EventService events, AccProperties properties) {
        this.jdbc = jdbc;
        this.events = events;
        this.properties = properties;
    }

    /**
     * Re-reads shortly after the turn ends.
     *
     * <p>Claude Code writes the assistant's final message *after* running the Stop
     * hook, so a read triggered by Stop always stops one record short. In a live
     * session the next tool call would pick it up, but the last reply of a session
     * would be lost forever — hence one short follow-up read.
     */
    public void ingestAfterTurn(String sessionId, String claudeSessionId, String transcriptPath) {
        ingest(sessionId, claudeSessionId, transcriptPath);
        if (!properties.transcriptCaptureEnabled() || transcriptPath == null) {
            return;
        }
        delayed.schedule(() -> {
            try {
                ingest(sessionId, claudeSessionId, transcriptPath);
            } catch (RuntimeException e) {
                log.debug("Follow-up transcript read failed: {}", e.getMessage());
            }
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * Emits an event for every conversation record written since the last call.
     *
     * @param sessionId       the ACC session
     * @param claudeSessionId the Claude Code session, used as the cursor key
     * @param transcriptPath  from the hook payload; ignored if missing or unreadable
     */
    public void ingest(String sessionId, String claudeSessionId, String transcriptPath) {
        if (!properties.transcriptCaptureEnabled()
                || transcriptPath == null || transcriptPath.isBlank()
                || claudeSessionId == null) {
            return;
        }
        Path file = Path.of(transcriptPath);
        if (!Files.isReadable(file)) {
            return;
        }

        ReentrantLock lock = locks.computeIfAbsent(claudeSessionId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            return; // another hook is already draining this transcript
        }
        try {
            long offset = cursor(claudeSessionId);
            long size = Files.size(file);
            if (size < offset) {
                // The file was replaced or truncated; start over rather than
                // reading from a position that now means something else.
                offset = 0;
            }
            if (size == offset) {
                return;
            }

            List<String> lines = new ArrayList<>();
            long newOffset;
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                raf.seek(offset);
                String line;
                while ((line = raf.readLine()) != null) {
                    // readLine() gives raw bytes as latin-1; re-decode as UTF-8.
                    lines.add(new String(line.getBytes(StandardCharsets.ISO_8859_1),
                            StandardCharsets.UTF_8));
                }
                newOffset = raf.getFilePointer();
            }

            int emitted = 0;
            for (String raw : lines) {
                emitted += handle(sessionId, raw);
            }
            saveCursor(claudeSessionId, newOffset);
            if (emitted > 0) {
                log.debug("Ingested {} conversation record(s) for session {}", emitted, sessionId);
            }
        } catch (IOException e) {
            log.debug("Could not read transcript {}: {}", transcriptPath, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /** @return how many events this record produced */
    private int handle(String sessionId, String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return 0;
        }
        JsonNode record = Json.read(trimmed);
        if (record.isNull()) {
            return 0;
        }
        String type = Json.text(record, "type");
        long when = timestampOf(record);
        JsonNode message = record.get("message");
        if (message == null || !message.isObject()) {
            return 0;
        }
        // Sidechain records belong to subagents, not this conversation.
        if (record.path("isSidechain").asBoolean(false)) {
            return 0;
        }

        JsonNode content = message.get("content");

        if ("user".equals(type)) {
            // A plain string is a prompt someone typed. Arrays are usually
            // tool_result blocks the hooks already recorded.
            if (content != null && content.isTextual()) {
                return emitPrompt(sessionId, content.asText(), when);
            }
            if (content != null && content.isArray()) {
                int n = 0;
                for (JsonNode block : content) {
                    if ("text".equals(Json.text(block, "type"))) {
                        n += emitPrompt(sessionId, Json.text(block, "text"), when);
                    }
                }
                return n;
            }
            return 0;
        }

        if ("assistant".equals(type) && content != null && content.isArray()) {
            int n = 0;
            for (JsonNode block : content) {
                String blockType = Json.text(block, "type");
                if ("text".equals(blockType)) {
                    n += emitAssistant(sessionId, EventType.ASSISTANT_TEXT,
                            Json.text(block, "text"), message, when);
                } else if ("thinking".equals(blockType)) {
                    n += emitAssistant(sessionId, EventType.THINKING,
                            Json.text(block, "thinking"), message, when);
                }
                // tool_use is intentionally skipped — the hook already has it.
            }
            return n;
        }
        return 0;
    }

    /** Transcript timestamps are ISO-8601; fall back to now if absent or odd. */
    private long timestampOf(JsonNode record) {
        String iso = Json.text(record, "timestamp");
        if (iso != null && !iso.isBlank()) {
            try {
                return Instant.parse(iso).toEpochMilli();
            } catch (RuntimeException e) {
                // fall through
            }
        }
        return System.currentTimeMillis();
    }

    private void emit(String sessionId, EventType type, String title, String payload, long when) {
        events.record(new AgentEvent(
                java.util.UUID.randomUUID().toString(),
                sessionId,
                events.nextSeq(sessionId),
                when,
                type, null, null, null, title, null, null, payload));
    }

    private int emitPrompt(String sessionId, String text, long when) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // Claude Code injects reminders and command scaffolding as user turns;
        // they are machinery, not something the developer typed.
        String stripped = text.strip();
        if (stripped.startsWith("<") && stripped.contains("</")) {
            return 0;
        }
        emit(sessionId, EventType.USER_PROMPT, summarise(stripped),
                Json.write(Map.of("text", clamp(stripped), "source", "transcript")), when);
        return 1;
    }

    private int emitAssistant(String sessionId, EventType type, String text, JsonNode message,
            long when) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", clamp(text));
        payload.put("source", "transcript");
        payload.put("model", Json.text(message, "model"));

        JsonNode usage = message.get("usage");
        if (usage != null && usage.isObject()) {
            Map<String, Object> tokens = new LinkedHashMap<>();
            tokens.put("input", usage.path("input_tokens").asLong(0));
            tokens.put("output", usage.path("output_tokens").asLong(0));
            tokens.put("cacheRead", usage.path("cache_read_input_tokens").asLong(0));
            tokens.put("cacheWrite", usage.path("cache_creation_input_tokens").asLong(0));
            tokens.put("thinking",
                    usage.path("output_tokens_details").path("thinking_tokens").asLong(0));
            payload.put("tokens", tokens);
        }

        emit(sessionId, type, summarise(text), Json.write(payload), when);
        return 1;
    }

    /**
     * Releases the per-session lock. One is created per Claude session and would
     * otherwise live as long as the daemon.
     */
    public void forget(String claudeSessionId) {
        if (claudeSessionId != null) {
            locks.remove(claudeSessionId);
        }
    }

    private long cursor(String claudeSessionId) {
        Long offset = jdbc.queryForObject(
                "SELECT COALESCE((SELECT byte_offset FROM transcript_cursors "
                        + "WHERE claude_session_id = ?), 0)", Long.class, claudeSessionId);
        return offset == null ? 0 : offset;
    }

    private void saveCursor(String claudeSessionId, long offset) {
        jdbc.update("""
                INSERT INTO transcript_cursors (claude_session_id, byte_offset, updated_at)
                VALUES (?,?,?)
                ON CONFLICT(claude_session_id) DO UPDATE SET
                    byte_offset = excluded.byte_offset,
                    updated_at  = excluded.updated_at
                """, claudeSessionId, offset, System.currentTimeMillis());
    }

    private String clamp(String text) {
        int limit = properties.textLimit();
        return text.length() <= limit
                ? text
                : text.substring(0, limit) + "\n… [" + (text.length() - limit) + " more characters]";
    }

    private String summarise(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 140 ? single : single.substring(0, 139) + "…";
    }
}
