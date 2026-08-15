package eu.nexuslayer.acc.approval;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.config.AccProperties;
import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentSession;
import eu.nexuslayer.acc.model.Approval;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.model.SessionStatus;
import eu.nexuslayer.acc.repo.ApprovalRepository;
import eu.nexuslayer.acc.runner.ToolSummary;
import eu.nexuslayer.acc.session.SessionService;
import eu.nexuslayer.acc.util.Json;
import eu.nexuslayer.acc.ws.Broadcaster;

/**
 * The approval gate.
 *
 * <p>Whether a tool call blocks is decided by the session it belongs to, not by
 * a global switch: a session launched in {@code acceptEdits} or
 * {@code bypassPermissions} was started to run unattended, so its hooks are
 * recorded and released immediately. Only interactive sessions actually wait for
 * a human.
 *
 * <p>Identical calls are collapsed onto one decision (see {@link RequestKey}) so
 * that Claude Code retrying a hook does not ask the developer twice.
 *
 * <p>Waits are capped below Claude Code's own hook timeout. On expiry the call is
 * denied with an explicit reason rather than silently allowed — an unattended
 * developer must never mean "yes".
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    /** How long a decision keeps satisfying repeats of the same call. */
    private static final long REPLAY_WINDOW_MS = 180_000;

    private final ApprovalRepository repository;
    private final SessionService sessions;
    private final EventService events;
    private final Broadcaster broadcaster;
    private final AccProperties properties;

    /** requestKey -> the gate every duplicate of that call waits on. */
    private final Map<String, Gate> inflight = new ConcurrentHashMap<>();
    /** approvalId -> gate, so the dashboard can resolve one by card id. */
    private final Map<String, Gate> byApprovalId = new ConcurrentHashMap<>();
    /** requestKey -> a decision recent enough to replay onto a retry. */
    private final Map<String, Replay> recent = new ConcurrentHashMap<>();

    public ApprovalService(ApprovalRepository repository, SessionService sessions, EventService events,
            Broadcaster broadcaster, AccProperties properties) {
        this.repository = repository;
        this.sessions = sessions;
        this.events = events;
        this.broadcaster = broadcaster;
        this.properties = properties;
    }

    public record Decision(boolean allowed, String reason, String status) {
        static Decision allow(String reason, String status) {
            return new Decision(true, reason, status);
        }

        static Decision deny(String reason, String status) {
            return new Decision(false, reason, status);
        }
    }

    private record Gate(String key, String approvalId, CompletableFuture<Decision> future) {
    }

    private record Replay(Decision decision, long at) {
        boolean isFresh() {
            return System.currentTimeMillis() - at < REPLAY_WINDOW_MS;
        }
    }

    /**
     * Called from the PreToolUse hook. Blocks the calling request thread until a
     * decision exists or the wait budget is exhausted.
     */
    public Decision evaluate(String claudeSessionId, String toolName, JsonNode toolInput) {
        Optional<AgentSession> owner = sessions.findByClaudeSessionId(claudeSessionId);
        String sessionId = owner.map(AgentSession::id).orElse(null);
        String risk = ToolSummary.risk(toolName, toolInput);
        String key = RequestKey.of(claudeSessionId, toolName, toolInput);

        // An unattended session, or one ACC does not own while in observe mode,
        // is released straight away — recorded, but never blocked.
        if (owner.isPresent() && owner.get().autoApprove()) {
            boolean adopted = "hook".equals(owner.get().origin());
            return settle(newApproval(sessionId, claudeSessionId, toolName, toolInput, risk),
                    Decision.allow(adopted
                            ? "observed: your own Claude Code session"
                            : "auto-approved: session running in " + owner.get().permissionMode(),
                            Approval.AUTO_APPROVED),
                    adopted);
        }
        if (owner.isEmpty() && !properties.gateUnknownSessions()) {
            return settle(newApproval(sessionId, claudeSessionId, toolName, toolInput, risk),
                    Decision.allow("observed: session not managed by ACC", Approval.AUTO_APPROVED),
                    true);
        }

        // The developer already ruled on this exact call moments ago; a retry
        // inherits that ruling instead of asking again.
        Replay replayed = recent.get(key);
        if (replayed != null && replayed.isFresh()) {
            log.info("Replaying {} decision for repeated {} call", replayed.decision.status(), toolName);
            return replayed.decision();
        }

        // A duplicate arriving while the original is still held joins that wait.
        Gate existing = inflight.get(key);
        if (existing != null) {
            log.info("Joining in-flight approval {} for repeated {} call", existing.approvalId(), toolName);
            return await(existing, sessionId, toolName);
        }

        Approval approval = newApproval(sessionId, claudeSessionId, toolName, toolInput, risk);
        Gate gate = new Gate(key, approval.id(), new CompletableFuture<>());

        Gate raced = inflight.putIfAbsent(key, gate);
        if (raced != null) {
            return await(raced, sessionId, toolName);
        }
        byApprovalId.put(approval.id(), gate);
        repository.save(approval);

        if (sessionId != null) {
            sessions.mutate(sessionId, s -> s.withStatus(SessionStatus.WAITING_APPROVAL));
            events.record(sessionId, EventType.APPROVAL_REQUEST,
                    ToolSummary.describe(toolName, toolInput, owner.map(AgentSession::cwd).orElse(null)),
                    Json.write(Map.of("approvalId", approval.id(), "tool", String.valueOf(toolName),
                            "risk", risk)));
        }
        broadcaster.broadcast("approval", approval);
        log.info("Approval {} pending for {} ({})", approval.id(), toolName, risk);

        return await(gate, sessionId, toolName);
    }

    private Decision await(Gate gate, String sessionId, String toolName) {
        try {
            Decision decision = gate.future().get(properties.approvalTimeoutSeconds(), TimeUnit.SECONDS);
            restoreRunning(sessionId);
            return decision;
        } catch (TimeoutException e) {
            Decision timedOut = Decision.deny(
                    "ACC approval timed out — no developer response. Ask again if still needed.",
                    Approval.TIMED_OUT);
            // Only the gate owner records the expiry; joiners just take the result.
            if (inflight.remove(gate.key(), gate)) {
                byApprovalId.remove(gate.approvalId());
                repository.findById(gate.approvalId()).ifPresent(a -> {
                    Approval expired = a.decided(Approval.TIMED_OUT,
                            "no response within " + properties.approvalTimeoutSeconds() + "s");
                    repository.save(expired);
                    broadcaster.broadcast("approval", expired);
                });
                gate.future().complete(timedOut);
                log.warn("Approval {} timed out; denying {}", gate.approvalId(), toolName);
            }
            restoreRunning(sessionId);
            return timedOut;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            restoreRunning(sessionId);
            return Decision.deny("ACC approval interrupted", Approval.TIMED_OUT);
        } catch (Exception e) {
            log.error("Approval wait failed for {}", gate.approvalId(), e);
            restoreRunning(sessionId);
            return Decision.deny("ACC approval failed: " + e.getMessage(), Approval.TIMED_OUT);
        }
    }

    /** Called from the dashboard when the developer clicks Approve or Deny. */
    public boolean decide(String approvalId, boolean allow, String reason) {
        Optional<Approval> found = repository.findById(approvalId);
        if (found.isEmpty() || !Approval.PENDING.equals(found.get().status())) {
            return false;
        }
        Approval decided = found.get().decided(allow ? Approval.APPROVED : Approval.DENIED, reason);
        repository.save(decided);
        broadcaster.broadcast("approval", decided);

        if (decided.sessionId() != null) {
            events.record(decided.sessionId(), EventType.APPROVAL_DECISION,
                    (allow ? "Approved " : "Denied ") + decided.toolName(),
                    Json.write(Map.of("approvalId", approvalId, "allowed", allow,
                            "reason", reason == null ? "" : reason)));
        }

        Decision decision = allow
                ? Decision.allow(reason == null ? "approved in ACC" : reason, Approval.APPROVED)
                : Decision.deny(reason == null ? "denied in ACC" : reason, Approval.DENIED);

        Gate gate = byApprovalId.remove(approvalId);
        if (gate == null) {
            return false;
        }
        inflight.remove(gate.key(), gate);
        // Remember it so the retry Claude Code fires seconds later is answered
        // automatically rather than raising a second card.
        recent.put(gate.key(), new Replay(decision, System.currentTimeMillis()));
        pruneReplays();

        return gate.future().complete(decision);
    }

    public List<Approval> pending() {
        return repository.findPending();
    }

    public List<Approval> forSession(String sessionId) {
        return repository.findBySession(sessionId);
    }

    private Approval newApproval(String sessionId, String claudeSessionId, String toolName,
            JsonNode toolInput, String risk) {
        return new Approval(
                UUID.randomUUID().toString(),
                sessionId,
                claudeSessionId,
                "PreToolUse",
                toolName,
                toolInput == null ? "{}" : toolInput.toString(),
                risk,
                Approval.PENDING,
                null,
                System.currentTimeMillis(),
                null);
    }

    private Decision settle(Approval approval, Decision decision, boolean adopted) {
        Approval resolved = approval.decided(decision.status(), decision.reason());
        repository.save(resolved);
        broadcaster.broadcast("approval", resolved);
        // An adopted session auto-approves every call by design, so an
        // "Auto-approved" node beside each one is noise, not information. The
        // approval row is still written, so the gate charts stay accurate.
        if (resolved.sessionId() != null && !adopted) {
            events.record(resolved.sessionId(), EventType.HOOK,
                    "Auto-approved " + resolved.toolName(),
                    Json.write(Map.of("reason", decision.reason(), "risk", resolved.risk())));
        }
        return decision;
    }

    private void restoreRunning(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessions.mutate(sessionId, s ->
                s.status() == SessionStatus.WAITING_APPROVAL ? s.withStatus(SessionStatus.RUNNING) : s);
    }

    private void pruneReplays() {
        recent.entrySet().removeIf(entry -> !entry.getValue().isFresh());
    }
}
