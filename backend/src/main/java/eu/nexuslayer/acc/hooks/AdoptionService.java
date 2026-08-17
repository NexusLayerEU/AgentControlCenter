package eu.nexuslayer.acc.hooks;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentEvent;
import eu.nexuslayer.acc.model.AgentSession;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.model.SessionStatus;
import eu.nexuslayer.acc.repo.SessionRepository;
import eu.nexuslayer.acc.runner.ToolSummary;
import eu.nexuslayer.acc.session.SessionService;
import eu.nexuslayer.acc.util.Json;
import eu.nexuslayer.acc.ws.Broadcaster;

/**
 * Makes the Claude Code sessions you start yourself visible in ACC.
 *
 * <p>ACC can only parse a structured stream for agents it launched. A session you
 * run in your own terminal never produces one — but its hooks carry enough to
 * rebuild the part that matters: {@code PreToolUse} gives the tool, its input and
 * a {@code tool_use_id}; {@code PostToolUse} gives the response for that same id.
 * That pairs into the same TOOL_CALL → TOOL_RESULT tree the dispatched sessions
 * produce, minus the assistant's prose.
 *
 * <p>Adopted sessions are <b>never gated</b> by default. You are already answering
 * Claude Code's own permission prompts in that terminal; a second gate in a
 * browser you may not be looking at would hang your own session for the full
 * approval timeout. The gate can still be armed per session from the dashboard.
 */
@Service
public class AdoptionService {

    private static final Logger log = LoggerFactory.getLogger(AdoptionService.class);

    private final SessionRepository sessions;
    private final SessionService sessionService;
    private final EventService events;
    private final Broadcaster broadcaster;
    private final TranscriptReader transcripts;

    /** claudeSessionId + tool_use_id -> the TOOL_CALL awaiting its PostToolUse. */
    private final Map<String, AgentEvent> openCalls = new ConcurrentHashMap<>();

    public AdoptionService(SessionRepository sessions, SessionService sessionService,
            EventService events, Broadcaster broadcaster, TranscriptReader transcripts) {
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.events = events;
        this.broadcaster = broadcaster;
        this.transcripts = transcripts;
    }

    /**
     * Returns the ACC session for this Claude Code session, creating one the first
     * time we hear from it.
     */
    public Optional<AgentSession> adopt(JsonNode body) {
        String claudeSessionId = Json.text(body, "session_id");
        if (claudeSessionId == null || claudeSessionId.isBlank()) {
            return Optional.empty();
        }

        Optional<AgentSession> existing = sessions.findByClaudeSessionId(claudeSessionId);
        if (existing.isPresent()) {
            return existing;
        }

        String cwd = Optional.ofNullable(Json.text(body, "cwd")).orElse(System.getProperty("user.dir"));
        String mode = Optional.ofNullable(Json.text(body, "permission_mode")).orElse("default");
        long now = System.currentTimeMillis();

        AgentSession adopted = new AgentSession(
                UUID.randomUUID().toString(),
                nameFor(cwd),
                cwd,
                null,
                null,
                mode,
                true, // observe only — see the class comment
                SessionStatus.RUNNING,
                "hook",
                claudeSessionId,
                null,
                now, now, null, null, null, null, null, null);

        sessions.save(adopted);
        broadcaster.broadcast("session", adopted);
        events.record(adopted.id(), EventType.SESSION_START,
                "Attached to a Claude Code session in " + nameFor(cwd),
                Json.write(Map.of("cwd", cwd, "permissionMode", mode, "origin", "hook")));
        log.info("Adopted external Claude Code session {} in {}", claudeSessionId, cwd);
        return Optional.of(adopted);
    }

    /**
     * Reads any new conversation records for an adopted session. Called from every
     * hook so prompts and replies land close to the tool calls they drove.
     */
    public void syncTranscript(JsonNode body) {
        adopt(body).ifPresent(session -> {
            if ("hook".equals(session.origin())) {
                transcripts.ingest(session.id(), session.claudeSessionId(),
                        Json.text(body, "transcript_path"));
            }
        });
    }

    /** PreToolUse: open a TOOL_CALL node and wake the session if it was idle. */
    public void recordToolCall(JsonNode body) {
        syncTranscript(body);
        adopt(body).ifPresent(session -> {
            if (!"hook".equals(session.origin())) {
                return; // dispatched sessions already get this from the JSON stream
            }
            String toolName = Json.text(body, "tool_name");
            String useId = Json.text(body, "tool_use_id");
            JsonNode input = body.get("tool_input");

            // ACC's hooks can legitimately be registered at BOTH global and project
            // scope, in which case Claude Code fires each one twice for the same
            // call. Claude Code also re-fires a hook it considers slow. Either way
            // the tool_use_id is stable, so the first node wins and repeats are
            // dropped rather than drawn as phantom duplicates in the tree.
            if (useId != null && openCalls.containsKey(key(session.claudeSessionId(), useId))) {
                return;
            }

            AgentEvent call = new AgentEvent(
                    UUID.randomUUID().toString(),
                    session.id(),
                    events.nextSeq(session.id()),
                    System.currentTimeMillis(),
                    EventType.TOOL_CALL,
                    null,
                    useId,
                    toolName,
                    ToolSummary.describe(toolName, input, session.cwd()),
                    "running",
                    null,
                    Json.write(Map.of(
                            "input", input == null ? Map.of() : Json.mapper().convertValue(input, Object.class),
                            "risk", ToolSummary.risk(toolName, input))));

            events.record(call);
            if (useId != null) {
                openCalls.put(key(session.claudeSessionId(), useId), call);
            }
            if (session.status() != SessionStatus.RUNNING) {
                sessionService.mutate(session.id(), s -> s.withStatus(SessionStatus.RUNNING));
            }
        });
    }

    /** PostToolUse: close the matching TOOL_CALL and attach its result. */
    public void recordToolResult(JsonNode body) {
        adopt(body).ifPresent(session -> {
            if (!"hook".equals(session.origin())) {
                return;
            }
            String useId = Json.text(body, "tool_use_id");
            AgentEvent call = useId == null ? null : openCalls.remove(key(session.claudeSessionId(), useId));
            JsonNode response = body.get("tool_response");
            boolean failed = isFailure(response);
            long now = System.currentTimeMillis();

            events.record(new AgentEvent(
                    UUID.randomUUID().toString(),
                    session.id(),
                    events.nextSeq(session.id()),
                    now,
                    EventType.TOOL_RESULT,
                    call == null ? null : call.id(),
                    useId,
                    call == null ? Json.text(body, "tool_name") : call.toolName(),
                    failed ? "failed" : "ok",
                    failed ? "error" : "ok",
                    call == null ? null : now - call.ts(),
                    Json.write(Map.of("output", preview(response), "isError", failed))));

            if (call != null) {
                events.update(call.withStatus(failed ? "error" : "ok").withDuration(now - call.ts()));
            }
        });
    }

    /**
     * Stop fires at the end of each assistant turn, not at the end of the session,
     * so the session goes idle rather than terminal — your window is still open.
     */
    public void markIdle(JsonNode body) {
        adopt(body).ifPresent(session -> {
            if ("hook".equals(session.origin())) {
                // Stop runs before the final assistant message is flushed.
                transcripts.ingestAfterTurn(session.id(), session.claudeSessionId(),
                        Json.text(body, "transcript_path"));
            }
            if ("hook".equals(session.origin()) && session.status() == SessionStatus.RUNNING) {
                sessionService.mutate(session.id(), s -> s.withStatus(SessionStatus.IDLE));
            }
        });
    }

    private String key(String claudeSessionId, String useId) {
        return claudeSessionId + '/' + useId;
    }

    private boolean isFailure(JsonNode response) {
        if (response == null || response.isNull()) {
            return false;
        }
        if (response.path("is_error").asBoolean(false)) {
            return true;
        }
        // Some tools report failure as a plain success flag instead.
        JsonNode success = response.get("success");
        return success != null && success.isBoolean() && !success.asBoolean();
    }

    /**
     * PostToolUse hands back a structured tool_response, not the plain text the
     * JSON stream carries. Pull out the part a human actually wants to read so
     * results look the same as they do for a dispatched session, instead of a
     * wall of escaped JSON.
     */
    private String preview(JsonNode response) {
        if (response == null || response.isNull()) {
            return "";
        }
        return truncate(extract(response));
    }

    private String extract(JsonNode response) {
        if (response.isTextual()) {
            return response.asText();
        }
        // Bash
        if (response.hasNonNull("stdout") || response.hasNonNull("stderr")) {
            String out = response.path("stdout").asText("");
            String err = response.path("stderr").asText("");
            String joined = err.isBlank() ? out : (out.isBlank() ? err : out + "\n" + err);
            return joined.isBlank() ? "(no output)" : joined;
        }
        // Read / Write / Edit wrap the payload in a file node
        JsonNode file = response.get("file");
        if (file != null && file.hasNonNull("content")) {
            return file.get("content").asText();
        }
        for (String field : new String[] { "content", "output", "result", "message" }) {
            JsonNode node = response.get(field);
            if (node != null && node.isTextual()) {
                return node.asText();
            }
        }
        return response.toPrettyString();
    }

    private String truncate(String text) {
        return text.length() <= 4000 ? text : text.substring(0, 4000) + "\n… truncated";
    }

    /** A folder name reads better in the session list than an absolute path. */
    private String nameFor(String cwd) {
        try {
            Path path = Path.of(cwd);
            Path leaf = path.getFileName();
            return leaf == null ? cwd : leaf.toString();
        } catch (Exception e) {
            return cwd;
        }
    }
}
