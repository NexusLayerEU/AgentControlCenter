package eu.nexuslayer.acc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acc")
public record AccProperties(
        String home,
        String claudeBinary,
        int approvalTimeoutSeconds,
        String unknownSessionPolicy) {

    public boolean gateUnknownSessions() {
        return "gate".equalsIgnoreCase(unknownSessionPolicy);
    }
}
