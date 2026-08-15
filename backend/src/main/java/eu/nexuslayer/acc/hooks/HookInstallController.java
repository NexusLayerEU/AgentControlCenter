package eu.nexuslayer.acc.hooks;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/hooks")
public class HookInstallController {

    private final HookInstaller installer;
    private final int port;

    public HookInstallController(HookInstaller installer, @Value("${server.port}") int port) {
        this.installer = installer;
        this.port = port;
    }

    public record ScopeRequest(Boolean projectScope, String projectDir) {
        boolean isProjectScope() {
            return Boolean.TRUE.equals(projectScope);
        }

        String dir() {
            return projectDir == null || projectDir.isBlank() ? System.getProperty("user.dir") : projectDir;
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam(required = false) String projectDir) {
        boolean global = installer.isInstalled(false, null);
        boolean project = projectDir != null && installer.isInstalled(true, projectDir);
        return Map.of("global", global, "project", project, "port", port);
    }

    @PostMapping("/install")
    public ResponseEntity<HookInstaller.InstallResult> install(
            @RequestBody(required = false) ScopeRequest request) {
        ScopeRequest scope = request == null ? new ScopeRequest(false, null) : request;
        try {
            return ResponseEntity.ok(installer.install(port, scope.isProjectScope(), scope.dir()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to install hooks: " + e.getMessage(), e);
        }
    }

    @PostMapping("/uninstall")
    public ResponseEntity<HookInstaller.InstallResult> uninstall(
            @RequestBody(required = false) ScopeRequest request) {
        ScopeRequest scope = request == null ? new ScopeRequest(false, null) : request;
        try {
            return ResponseEntity.ok(installer.uninstall(scope.isProjectScope(), scope.dir()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to uninstall hooks: " + e.getMessage(), e);
        }
    }
}
