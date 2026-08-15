package eu.nexuslayer.acc.model;

/**
 * A single agent run tracked by ACC. Immutable: every state change produces a
 * new instance via the {@code with*} helpers.
 */
public record AgentSession(
        String id,
        String name,
        String cwd,
        String prompt,
        String model,
        String permissionMode,
        boolean autoApprove,
        SessionStatus status,
        String origin,
        String claudeSessionId,
        Long pid,
        long createdAt,
        long updatedAt,
        Long endedAt,
        Integer exitCode,
        String resultText,
        Double totalCostUsd,
        Integer numTurns,
        Long durationMs) {

    public AgentSession withStatus(SessionStatus next) {
        return new AgentSession(id, name, cwd, prompt, model, permissionMode, autoApprove, next, origin,
                claudeSessionId, pid, createdAt, System.currentTimeMillis(), endedAt, exitCode, resultText,
                totalCostUsd, numTurns, durationMs);
    }

    public AgentSession withClaudeSessionId(String next) {
        return new AgentSession(id, name, cwd, prompt, model, permissionMode, autoApprove, status, origin,
                next, pid, createdAt, System.currentTimeMillis(), endedAt, exitCode, resultText,
                totalCostUsd, numTurns, durationMs);
    }

    public AgentSession withPid(Long next) {
        return new AgentSession(id, name, cwd, prompt, model, permissionMode, autoApprove, status, origin,
                claudeSessionId, next, createdAt, System.currentTimeMillis(), endedAt, exitCode, resultText,
                totalCostUsd, numTurns, durationMs);
    }

    public AgentSession withAutoApprove(boolean next) {
        return new AgentSession(id, name, cwd, prompt, model, permissionMode, next, status, origin,
                claudeSessionId, pid, createdAt, System.currentTimeMillis(), endedAt, exitCode, resultText,
                totalCostUsd, numTurns, durationMs);
    }

    public AgentSession completed(SessionStatus finalStatus, Integer exit, String result,
            Double cost, Integer turns, Long durationMillis) {
        long now = System.currentTimeMillis();
        return new AgentSession(id, name, cwd, prompt, model, permissionMode, autoApprove, finalStatus, origin,
                claudeSessionId, pid, createdAt, now, now, exit, result, cost, turns, durationMillis);
    }
}
