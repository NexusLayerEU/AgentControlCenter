package eu.nexuslayer.acc.session;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import eu.nexuslayer.acc.approval.ApprovalService;
import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentEvent;
import eu.nexuslayer.acc.model.AgentSession;
import eu.nexuslayer.acc.model.Approval;
import eu.nexuslayer.acc.runner.StartSessionRequest;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessions;
    private final EventService events;
    private final ApprovalService approvals;

    public SessionController(SessionService sessions, EventService events, ApprovalService approvals) {
        this.sessions = sessions;
        this.events = events;
        this.approvals = approvals;
    }

    @GetMapping
    public List<AgentSession> list(@RequestParam(defaultValue = "100") int limit) {
        return sessions.list(Math.min(limit, 500));
    }

    @PostMapping
    public ResponseEntity<AgentSession> start(@RequestBody StartSessionRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prompt is required");
        }
        try {
            return ResponseEntity.ok(sessions.start(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @GetMapping("/{id}")
    public AgentSession get(@PathVariable String id) {
        return sessions.find(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such session"));
    }

    @GetMapping("/{id}/events")
    public List<AgentEvent> timeline(@PathVariable String id) {
        return events.timeline(id);
    }

    @GetMapping("/{id}/approvals")
    public List<Approval> approvals(@PathVariable String id) {
        return approvals.forSession(id);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        boolean killed = sessions.cancel(id);
        return ResponseEntity.ok(Map.of("killed", killed));
    }

    @PostMapping("/{id}/auto-approve")
    public AgentSession setAutoApprove(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return sessions.mutate(id, s -> s.withAutoApprove(enabled))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such session"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sessions.delete(id);
        return ResponseEntity.noContent().build();
    }
}
