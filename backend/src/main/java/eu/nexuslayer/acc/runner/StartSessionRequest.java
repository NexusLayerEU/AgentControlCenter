package eu.nexuslayer.acc.runner;

/**
 * Payload for POST /api/sessions.
 *
 * <p>{@code permissionMode} drives the approval gate: anything other than
 * {@code default} means the agent was launched to run unattended, so ACC records
 * tool calls but never blocks on them.
 */
public record StartSessionRequest(
        String name,
        String prompt,
        String cwd,
        String model,
        String permissionMode,
        Boolean autoApprove) {

    public static final String MODE_DEFAULT = "default";
    public static final String MODE_ACCEPT_EDITS = "acceptEdits";
    public static final String MODE_PLAN = "plan";
    public static final String MODE_BYPASS = "bypassPermissions";

    public String resolvedMode() {
        if (permissionMode == null || permissionMode.isBlank()) {
            return MODE_DEFAULT;
        }
        return switch (permissionMode) {
            case MODE_ACCEPT_EDITS, MODE_PLAN, MODE_BYPASS, MODE_DEFAULT -> permissionMode;
            default -> MODE_DEFAULT;
        };
    }

    /**
     * An agent running in acceptEdits or bypassPermissions was explicitly started
     * to proceed without a human, so ACC must not hold its hooks open. An explicit
     * autoApprove flag still wins if the caller sets one.
     */
    public boolean resolvedAutoApprove() {
        if (autoApprove != null) {
            return autoApprove;
        }
        String mode = resolvedMode();
        return MODE_ACCEPT_EDITS.equals(mode) || MODE_BYPASS.equals(mode);
    }

    public String resolvedName() {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        if (prompt == null || prompt.isBlank()) {
            return "untitled session";
        }
        String single = prompt.replaceAll("\\s+", " ").trim();
        return single.length() <= 60 ? single : single.substring(0, 59) + "…";
    }
}
