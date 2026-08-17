package eu.nexuslayer.acc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acc")
public record AccProperties(
        String home,
        String claudeBinary,
        int approvalTimeoutSeconds,
        String unknownSessionPolicy,
        Boolean captureTranscript,
        Integer transcriptTextLimit) {

    public boolean gateUnknownSessions() {
        return "gate".equalsIgnoreCase(unknownSessionPolicy);
    }

    /**
     * Whether to read the prompts and replies out of Claude Code's transcript for
     * sessions ACC did not launch. On by default — without it an adopted session
     * shows tool calls with no idea what was asked or answered.
     */
    public boolean transcriptCaptureEnabled() {
        return captureTranscript == null || captureTranscript;
    }

    /** Characters of any single prompt or reply kept; the rest is elided. */
    public int textLimit() {
        return transcriptTextLimit == null || transcriptTextLimit <= 0 ? 8000 : transcriptTextLimit;
    }
}
