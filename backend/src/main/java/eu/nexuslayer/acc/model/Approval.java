package eu.nexuslayer.acc.model;

public record Approval(
        String id,
        String sessionId,
        String claudeSessionId,
        String hookEvent,
        String toolName,
        String toolInput,
        String risk,
        String status,
        String reason,
        long createdAt,
        Long decidedAt) {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String DENIED = "denied";
    public static final String TIMED_OUT = "timed_out";
    public static final String AUTO_APPROVED = "auto_approved";

    public Approval decided(String nextStatus, String nextReason) {
        return new Approval(id, sessionId, claudeSessionId, hookEvent, toolName, toolInput, risk,
                nextStatus, nextReason, createdAt, System.currentTimeMillis());
    }
}
