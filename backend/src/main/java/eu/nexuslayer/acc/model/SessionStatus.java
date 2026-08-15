package eu.nexuslayer.acc.model;

public enum SessionStatus {
    STARTING,
    RUNNING,
    WAITING_APPROVAL,
    /**
     * An adopted session between turns: the developer's Claude Code window is
     * still open, the agent just is not doing anything this second. Not terminal —
     * the next tool call moves it back to RUNNING.
     */
    IDLE,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
