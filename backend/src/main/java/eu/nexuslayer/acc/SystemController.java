package eu.nexuslayer.acc;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.nexuslayer.acc.config.AccProperties;
import eu.nexuslayer.acc.runner.ClaudeLauncher;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final AccProperties properties;
    private final int port;

    public SystemController(AccProperties properties, @Value("${server.port}") int port) {
        this.properties = properties;
        this.port = port;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("port", port);
        status.put("home", AccPaths.home().toString());
        status.put("cwd", System.getProperty("user.dir"));
        status.put("approvalTimeoutSeconds", properties.approvalTimeoutSeconds());
        status.put("unknownSessionPolicy", properties.unknownSessionPolicy());
        status.put("claude", probeClaude());
        return status;
    }

    /** Confirms the configured claude launcher is actually callable. */
    private Map<String, Object> probeClaude() {
        String resolved = ClaudeLauncher.resolve(properties.claudeBinary());
        String version = ClaudeLauncher.version(properties.claudeBinary());
        return version == null
                ? Map.of("available", false, "binary", resolved, "error", "not found or not runnable")
                : Map.of("available", true, "binary", resolved, "version", version);
    }
}
