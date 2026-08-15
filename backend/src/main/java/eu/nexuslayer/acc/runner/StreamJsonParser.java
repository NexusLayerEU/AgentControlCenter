package eu.nexuslayer.acc.runner;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentEvent;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.util.Json;

/**
 * Translates Claude Code's {@code --output-format stream-json} lines into ACC
 * activity-tree nodes.
 *
 * <p>The stream emits one JSON object per line. The shapes that matter:
 * <ul>
 *   <li>{@code {"type":"system","subtype":"init","session_id":...}} — run metadata</li>
 *   <li>{@code {"type":"assistant","message":{"content":[...]}}} — text / thinking / tool_use blocks</li>
 *   <li>{@code {"type":"user","message":{"content":[{"type":"tool_result",...}]}}} — tool output</li>
 *   <li>{@code {"type":"result",...}} — terminal summary with cost and turn count</li>
 * </ul>
 */
public class StreamJsonParser {

    private static final Logger log = LoggerFactory.getLogger(StreamJsonParser.class);
    private static final int RESULT_PREVIEW_CHARS = 4000;

    private final String sessionId;
    private final String workingDir;
    private final EventService events;
    private final Consumer<JsonNode> onSystemInit;
    private final Consumer<JsonNode> onResult;

    /** tool_use_id -> the TOOL_CALL node awaiting its result. */
    private final Map<String, AgentEvent> openToolCalls = new HashMap<>();

    public StreamJsonParser(String sessionId, String workingDir, EventService events,
            Consumer<JsonNode> onSystemInit, Consumer<JsonNode> onResult) {
        this.sessionId = sessionId;
        this.workingDir = workingDir;
        this.events = events;
        this.onSystemInit = onSystemInit;
        this.onResult = onResult;
    }

    public void accept(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return;
        }
        JsonNode node = Json.read(trimmed);
        if (node.isNull()) {
            log.debug("Skipping unparseable stream line: {}", preview(trimmed));
            return;
        }

        String type = Json.text(node, "type");
        if (type == null) {
            return;
        }
        try {
            switch (type) {
                case "system" -> handleSystem(node);
                case "assistant" -> handleAssistant(node);
                case "user" -> handleUser(node);
                case "result" -> handleResult(node);
                default -> log.debug("Ignoring stream event type '{}'", type);
            }
        } catch (Exception e) {
            log.warn("Failed to handle stream event '{}': {}", type, e.getMessage());
            events.record(sessionId, EventType.ERROR, "Stream parse error",
                    Json.write(Map.of("message", String.valueOf(e.getMessage()), "line", preview(trimmed))));
        }
    }

    private void handleSystem(JsonNode node) {
        if ("init".equals(Json.text(node, "subtype"))) {
            onSystemInit.accept(node);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", Json.text(node, "model"));
            payload.put("cwd", Json.text(node, "cwd"));
            payload.put("permissionMode", Json.text(node, "permissionMode"));
            payload.put("tools", node.get("tools"));
            events.record(sessionId, EventType.SESSION_START, "Agent initialised", Json.write(payload));
        }
    }

    private void handleAssistant(JsonNode node) {
        JsonNode content = node.path("message").path("content");
        if (!content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            String blockType = Json.text(block, "type");
            if (blockType == null) {
                continue;
            }
            switch (blockType) {
                case "text" -> {
                    String text = Json.text(block, "text");
                    if (text != null && !text.isBlank()) {
                        events.record(sessionId, EventType.ASSISTANT_TEXT, summarise(text),
                                Json.write(Map.of("text", text)));
                    }
                }
                case "thinking" -> {
                    String thinking = Json.text(block, "thinking");
                    if (thinking != null && !thinking.isBlank()) {
                        events.record(sessionId, EventType.THINKING, summarise(thinking),
                                Json.write(Map.of("text", thinking)));
                    }
                }
                case "tool_use" -> recordToolCall(block);
                default -> {
                    // redacted_thinking and future block types are intentionally ignored
                }
            }
        }
    }

    private void recordToolCall(JsonNode block) {
        String useId = Json.text(block, "id");
        String toolName = Json.text(block, "name");
        JsonNode input = block.get("input");

        AgentEvent event = new AgentEvent(
                java.util.UUID.randomUUID().toString(),
                sessionId,
                events.nextSeq(sessionId),
                System.currentTimeMillis(),
                EventType.TOOL_CALL,
                null,
                useId,
                toolName,
                ToolSummary.describe(toolName, input, workingDir),
                "running",
                null,
                Json.write(Map.of(
                        "input", input == null ? Map.of() : Json.mapper().convertValue(input, Object.class),
                        "risk", ToolSummary.risk(toolName, input))));

        events.record(event);
        if (useId != null) {
            openToolCalls.put(useId, event);
        }
    }

    private void handleUser(JsonNode node) {
        JsonNode content = node.path("message").path("content");
        if (!content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            if (!"tool_result".equals(Json.text(block, "type"))) {
                continue;
            }
            String useId = Json.text(block, "tool_use_id");
            AgentEvent call = useId == null ? null : openToolCalls.remove(useId);
            boolean isError = block.path("is_error").asBoolean(false);
            String resultText = extractResultText(block);

            AgentEvent result = new AgentEvent(
                    java.util.UUID.randomUUID().toString(),
                    sessionId,
                    events.nextSeq(sessionId),
                    System.currentTimeMillis(),
                    EventType.TOOL_RESULT,
                    call == null ? null : call.id(),
                    useId,
                    call == null ? null : call.toolName(),
                    isError ? "failed" : "ok",
                    isError ? "error" : "ok",
                    call == null ? null : System.currentTimeMillis() - call.ts(),
                    Json.write(Map.of("output", truncate(resultText), "isError", isError)));
            events.record(result);

            if (call != null) {
                events.update(call
                        .withStatus(isError ? "error" : "ok")
                        .withDuration(System.currentTimeMillis() - call.ts()));
            }
        }
    }

    private void handleResult(JsonNode node) {
        onResult.accept(node);
    }

    private String extractResultText(JsonNode block) {
        JsonNode content = block.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(Json.text(part, "type"))) {
                    sb.append(Json.text(part, "text")).append('\n');
                } else {
                    sb.append('[').append(Json.text(part, "type")).append(']').append('\n');
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= RESULT_PREVIEW_CHARS
                ? value
                : value.substring(0, RESULT_PREVIEW_CHARS) + "\n… [" + (value.length() - RESULT_PREVIEW_CHARS)
                        + " more characters]";
    }

    private static String summarise(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 140 ? single : single.substring(0, 139) + "…";
    }

    private static String preview(String line) {
        return line.length() <= 200 ? line : line.substring(0, 200) + "…";
    }
}
