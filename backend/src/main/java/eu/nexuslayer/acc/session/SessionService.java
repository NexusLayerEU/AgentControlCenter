package eu.nexuslayer.acc.session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentSession;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.model.SessionStatus;
import eu.nexuslayer.acc.repo.ApprovalRepository;
import eu.nexuslayer.acc.repo.SessionRepository;
import eu.nexuslayer.acc.runner.ClaudeRunner;
import eu.nexuslayer.acc.runner.StartSessionRequest;
import eu.nexuslayer.acc.util.Json;
import eu.nexuslayer.acc.ws.Broadcaster;
import jakarta.annotation.PostConstruct;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessions;
    private final ApprovalRepository approvals;
    private final EventService events;
    private final ClaudeRunner runner;
    private final Broadcaster broadcaster;

    public SessionService(SessionRepository sessions, ApprovalRepository approvals, EventService events,
            ClaudeRunner runner, Broadcaster broadcaster) {
        this.sessions = sessions;
        this.approvals = approvals;
        this.events = events;
        this.runner = runner;
        this.broadcaster = broadcaster;
    }

    @PostConstruct
    public void reconcileAfterRestart() {
        int orphanedSessions = sessions.markOrphansFailed();
        int orphanedApprovals = approvals.expireAllPending();
        if (orphanedSessions > 0 || orphanedApprovals > 0) {
            log.info("Reconciled {} orphaned session(s) and {} pending approval(s) after restart",
                    orphanedSessions, orphanedApprovals);
        }
    }

    public AgentSession start(StartSessionRequest request) {
        String cwd = resolveCwd(request.cwd());
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        AgentSession session = new AgentSession(
                id,
                request.resolvedName(),
                cwd,
                request.prompt(),
                request.model(),
                request.resolvedMode(),
                request.resolvedAutoApprove(),
                SessionStatus.STARTING,
                "acc",
                null, null,
                now, now, null, null, null, null, null, null);

        sessions.save(session);
        broadcaster.broadcast("session", session);
        events.record(id, EventType.USER_PROMPT, request.resolvedName(),
                Json.write(Map.of("prompt", request.prompt(), "cwd", cwd,
                        "permissionMode", request.resolvedMode(),
                        "autoApprove", request.resolvedAutoApprove())));

        StartSessionRequest resolved = new StartSessionRequest(session.name(), request.prompt(), cwd,
                request.model(), request.resolvedMode(), request.resolvedAutoApprove());

        runner.start(id, resolved, new ClaudeRunner.Callbacks() {
            @Override
            public void onStarted(long pid) {
                mutate(id, s -> s.withPid(pid).withStatus(SessionStatus.RUNNING));
            }

            @Override
            public void onClaudeSessionId(String claudeSessionId) {
                if (claudeSessionId != null) {
                    mutate(id, s -> s.withClaudeSessionId(claudeSessionId));
                }
            }

            @Override
            public void onResult(JsonNode result) {
                finish(id, result);
            }

            @Override
            public void onExit(int exitCode) {
                sessions.findById(id).ifPresent(s -> {
                    if (!s.status().isTerminal()) {
                        AgentSession done = s.completed(
                                exitCode == 0 ? SessionStatus.COMPLETED : SessionStatus.FAILED,
                                exitCode, s.resultText(), s.totalCostUsd(), s.numTurns(),
                                System.currentTimeMillis() - s.createdAt());
                        sessions.save(done);
                        broadcaster.broadcast("session", done);
                    }
                });
                events.forget(id);
            }
        });

        return session;
    }

    private void finish(String id, JsonNode result) {
        sessions.findById(id).ifPresent(s -> {
            boolean errored = result.path("is_error").asBoolean(false);
            AgentSession done = s.completed(
                    errored ? SessionStatus.FAILED : SessionStatus.COMPLETED,
                    errored ? 1 : 0,
                    Json.text(result, "result"),
                    result.hasNonNull("total_cost_usd") ? result.get("total_cost_usd").asDouble() : null,
                    result.hasNonNull("num_turns") ? result.get("num_turns").asInt() : null,
                    result.hasNonNull("duration_ms") ? result.get("duration_ms").asLong()
                            : System.currentTimeMillis() - s.createdAt());
            sessions.save(done);
            broadcaster.broadcast("session", done);
            events.record(id, EventType.SESSION_END,
                    errored ? "Run failed" : "Run complete",
                    Json.write(Map.of(
                            "result", String.valueOf(Json.text(result, "result")),
                            "costUsd", done.totalCostUsd() == null ? 0d : done.totalCostUsd(),
                            "turns", done.numTurns() == null ? 0 : done.numTurns(),
                            "durationMs", done.durationMs() == null ? 0L : done.durationMs())));
        });
    }

    /** Applies a change to the stored session and pushes the new state to the UI. */
    public Optional<AgentSession> mutate(String id, java.util.function.UnaryOperator<AgentSession> change) {
        Optional<AgentSession> current = sessions.findById(id);
        current.ifPresent(s -> {
            AgentSession next = change.apply(s);
            sessions.save(next);
            broadcaster.broadcast("session", next);
        });
        return sessions.findById(id);
    }

    public List<AgentSession> list(int limit) {
        return sessions.findAll(limit);
    }

    public Optional<AgentSession> find(String id) {
        return sessions.findById(id);
    }

    public Optional<AgentSession> findByClaudeSessionId(String claudeSessionId) {
        return sessions.findByClaudeSessionId(claudeSessionId);
    }

    public boolean cancel(String id) {
        boolean killed = runner.cancel(id);
        mutate(id, s -> s.completed(SessionStatus.CANCELLED, 130, s.resultText(), s.totalCostUsd(),
                s.numTurns(), System.currentTimeMillis() - s.createdAt()));
        return killed;
    }

    public void delete(String id) {
        runner.cancel(id);
        sessions.delete(id);
        events.forget(id);
        broadcaster.broadcast("session:deleted", Map.of("id", id));
    }

    private String resolveCwd(String requested) {
        if (requested == null || requested.isBlank()) {
            return System.getProperty("user.dir");
        }
        Path path = Path.of(requested.replaceFirst("^~", System.getProperty("user.home")));
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Working directory does not exist: " + requested);
        }
        return path.toAbsolutePath().toString();
    }
}
