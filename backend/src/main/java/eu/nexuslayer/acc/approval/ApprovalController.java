package eu.nexuslayer.acc.approval;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.nexuslayer.acc.model.Approval;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    public record DecisionRequest(String reason) {
    }

    @GetMapping("/pending")
    public List<Approval> pending() {
        return approvals.pending();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id,
            @RequestBody(required = false) DecisionRequest request) {
        boolean delivered = approvals.decide(id, true, request == null ? null : request.reason());
        return respond(delivered);
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<Map<String, Object>> deny(@PathVariable String id,
            @RequestBody(required = false) DecisionRequest request) {
        boolean delivered = approvals.decide(id, false, request == null ? null : request.reason());
        return respond(delivered);
    }

    private ResponseEntity<Map<String, Object>> respond(boolean delivered) {
        // A recorded-but-undelivered decision means the agent's hook already gave
        // up waiting; the UI needs to say so rather than claim success.
        return ResponseEntity.ok(Map.of(
                "delivered", delivered,
                "message", delivered ? "decision sent to agent"
                        : "decision recorded, but the agent stopped waiting"));
    }
}
