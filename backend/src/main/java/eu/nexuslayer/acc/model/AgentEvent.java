package eu.nexuslayer.acc.model;

import java.util.UUID;

/**
 * One node in a session's activity tree. {@code parentId} links a tool result
 * back to the tool call that produced it, which is what the UI renders as a
 * tree and as a flow graph.
 */
public record AgentEvent(
        String id,
        String sessionId,
        long seq,
        long ts,
        EventType type,
        String parentId,
        String toolUseId,
        String toolName,
        String title,
        String status,
        Long durationMs,
        String payload) {

    public static AgentEvent of(String sessionId, long seq, EventType type, String title, String payload) {
        return new AgentEvent(UUID.randomUUID().toString(), sessionId, seq, System.currentTimeMillis(),
                type, null, null, null, title, null, null, payload);
    }

    public AgentEvent withParent(String parent) {
        return new AgentEvent(id, sessionId, seq, ts, type, parent, toolUseId, toolName, title, status,
                durationMs, payload);
    }

    public AgentEvent withTool(String useId, String name) {
        return new AgentEvent(id, sessionId, seq, ts, type, parentId, useId, name, title, status,
                durationMs, payload);
    }

    public AgentEvent withStatus(String next) {
        return new AgentEvent(id, sessionId, seq, ts, type, parentId, toolUseId, toolName, title, next,
                durationMs, payload);
    }

    public AgentEvent withDuration(Long millis) {
        return new AgentEvent(id, sessionId, seq, ts, type, parentId, toolUseId, toolName, title, status,
                millis, payload);
    }
}
