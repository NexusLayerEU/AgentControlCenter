package eu.nexuslayer.acc.pty;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.nexuslayer.acc.Platform;

@RestController
@RequestMapping("/api/terminals")
public class PtyController {

    private final PtyRegistry registry;

    public PtyController(PtyRegistry registry) {
        this.registry = registry;
    }

    public record OpenRequest(String cwd, String command, Integer cols, Integer rows) {
    }

    @PostMapping("/{id}/open")
    public ResponseEntity<Map<String, Object>> open(@PathVariable String id, @RequestBody OpenRequest request) {
        String cwd = request.cwd() == null || request.cwd().isBlank()
                ? System.getProperty("user.home")
                : request.cwd();
        String[] command = request.command() == null || request.command().isBlank()
                ? Platform.loginShell().toArray(String[]::new)
                : Platform.shellCommand(request.command()).toArray(String[]::new);

        boolean opened = registry.open(id, cwd, command,
                request.cols() == null ? 120 : request.cols(),
                request.rows() == null ? 32 : request.rows());
        return ResponseEntity.ok(Map.of("opened", opened, "id", id));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable String id) {
        registry.close(id);
        return ResponseEntity.ok(Map.of("closed", true));
    }
}
