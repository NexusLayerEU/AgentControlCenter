package eu.nexuslayer.acc.model;

public enum EventType {
    SESSION_START,
    USER_PROMPT,
    ASSISTANT_TEXT,
    THINKING,
    TOOL_CALL,
    TOOL_RESULT,
    APPROVAL_REQUEST,
    APPROVAL_DECISION,
    HOOK,
    SYSTEM,
    ERROR,
    SESSION_END
}
