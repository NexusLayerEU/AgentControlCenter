package eu.nexuslayer.acc.approval;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.hooks.AdoptionService;
import eu.nexuslayer.acc.util.Json;

/**
 * Endpoints that Claude Code's hooks POST into.
 *
 * <p>The hook script pipes its stdin JSON here and echoes our response back to
 * Claude Code verbatim, so the shapes returned must match what Claude Code
 * expects for each hook event.
 *
 * <p>Every event also adopts the sending session, which is what makes the Claude
 * Code windows you drive yourself show up in the dashboard alongside the ones ACC
 * dispatched.
 */
@RestController
@RequestMapping("/hooks")
public class HookController {

    private final ApprovalService approvals;
    private final AdoptionService adoption;

    public HookController(ApprovalService approvals, AdoptionService adoption) {
        this.approvals = approvals;
        this.adoption = adoption;
    }

    @PostMapping("/pre-tool-use")
    public ResponseEntity<Map<String, Object>> preToolUse(@RequestBody JsonNode body) {
        // Record first: the call should appear in the tree even if the gate then
        // holds it, so the developer can see what they are being asked about.
        adoption.recordToolCall(body);

        ApprovalService.Decision decision = approvals.evaluate(
                Json.text(body, "session_id"),
                Json.text(body, "tool_name"),
                body.get("tool_input"));

        Map<String, Object> hookOutput = new LinkedHashMap<>();
        hookOutput.put("hookEventName", "PreToolUse");
        hookOutput.put("permissionDecision", decision.allowed() ? "allow" : "deny");
        hookOutput.put("permissionDecisionReason", decision.reason());

        return ResponseEntity.ok(Map.of("hookSpecificOutput", hookOutput));
    }

    @PostMapping("/post-tool-use")
    public ResponseEntity<Map<String, Object>> postToolUse(@RequestBody JsonNode body) {
        adoption.recordToolResult(body);
        return ResponseEntity.ok(Map.of("continue", true));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(@RequestBody JsonNode body) {
        adoption.markIdle(body);
        return ResponseEntity.ok(Map.of("continue", true));
    }

    @PostMapping("/session-start")
    public ResponseEntity<Map<String, Object>> sessionStart(@RequestBody JsonNode body) {
        adoption.adopt(body);
        return ResponseEntity.ok(Map.of("continue", true));
    }

    @PostMapping("/notification")
    public ResponseEntity<Map<String, Object>> notification(@RequestBody JsonNode body) {
        adoption.adopt(body);
        return ResponseEntity.ok(Map.of("continue", true));
    }
}
