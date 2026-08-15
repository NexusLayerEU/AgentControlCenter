package eu.nexuslayer.acc.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.config.AccProperties;
import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.AgentSession;
import eu.nexuslayer.acc.model.Approval;
import eu.nexuslayer.acc.model.SessionStatus;
import eu.nexuslayer.acc.repo.ApprovalRepository;
import eu.nexuslayer.acc.session.SessionService;
import eu.nexuslayer.acc.util.Json;

/**
 * The gate decides whether an agent is allowed to act, so every branch is
 * asserted: released immediately, blocked until a human answers, denied on
 * silence, and collapsed when Claude Code retries the same call.
 */
class ApprovalServiceTest {

    private static final String CLAUDE_SESSION = "claude-abc";
    private static final JsonNode ECHO = Json.read("{\"command\":\"echo hi\"}");

    /** In-memory stand-in so the gate can be tested without SQLite. */
    private static class FakeApprovalRepository extends ApprovalRepository {
        final Map<String, Approval> rows = new ConcurrentHashMap<>();

        FakeApprovalRepository() {
            super(null);
        }

        @Override
        public void save(Approval a) {
            rows.put(a.id(), a);
        }

        @Override
        public Optional<Approval> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<Approval> findPending() {
            return rows.values().stream().filter(a -> Approval.PENDING.equals(a.status())).toList();
        }
    }

    private FakeApprovalRepository repository;
    private SessionService sessions;
    private EventService events;
    private List<Object> broadcasts;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        repository = new FakeApprovalRepository();
        sessions = mock(SessionService.class);
        events = mock(EventService.class);
        broadcasts = new CopyOnWriteArrayList<>();
        pool = Executors.newCachedThreadPool();
        when(sessions.mutate(anyString(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    private ApprovalService service(int timeoutSeconds, String unknownPolicy) {
        return new ApprovalService(repository, sessions, events,
                (channel, payload) -> broadcasts.add(payload),
                new AccProperties("/tmp/acc", "claude", timeoutSeconds, unknownPolicy));
    }

    private AgentSession session(boolean autoApprove, String mode) {
        long now = System.currentTimeMillis();
        return new AgentSession("acc-1", "n", "/tmp", "p", null, mode, autoApprove,
                SessionStatus.RUNNING, "acc", CLAUDE_SESSION, 1L, now, now,
                null, null, null, null, null, null);
    }

    private void owned(AgentSession s) {
        when(sessions.findByClaudeSessionId(CLAUDE_SESSION)).thenReturn(Optional.of(s));
    }

    private void unowned() {
        when(sessions.findByClaudeSessionId(anyString())).thenReturn(Optional.empty());
    }

    // ── released without blocking ───────────────────────────────────────────

    @Test
    @DisplayName("an unattended session is allowed immediately and never held")
    void autoApproveSessionIsReleasedAtOnce() {
        owned(session(true, "acceptEdits"));
        ApprovalService service = service(50, "observe");

        long start = System.currentTimeMillis();
        ApprovalService.Decision decision = service.evaluate(CLAUDE_SESSION, "Bash", ECHO);

        assertTrue(decision.allowed());
        assertEquals(Approval.AUTO_APPROVED, decision.status());
        assertTrue(decision.reason().contains("acceptEdits"));
        assertTrue(System.currentTimeMillis() - start < 1000, "must not block");
        assertTrue(service.pending().isEmpty(), "an auto-approved call raises no card");
    }

    @Test
    @DisplayName("a session ACC does not own is observed, not gated, under the default policy")
    void unknownSessionIsObserved() {
        unowned();
        ApprovalService.Decision decision = service(50, "observe")
                .evaluate("someone-elses-session", "Bash", ECHO);

        assertTrue(decision.allowed());
        assertEquals(Approval.AUTO_APPROVED, decision.status());
        assertTrue(decision.reason().contains("not managed by ACC"));
    }

    @Test
    @DisplayName("under the gate policy an unknown session is held like any other")
    void unknownSessionIsGatedWhenConfigured() throws Exception {
        unowned();
        ApprovalService service = service(1, "gate");

        ApprovalService.Decision decision = pool
                .submit(() -> service.evaluate("someone-elses-session", "Bash", ECHO))
                .get(10, TimeUnit.SECONDS);

        assertFalse(decision.allowed(), "it was held and then expired rather than waved through");
        assertEquals(Approval.TIMED_OUT, decision.status());
    }

    // ── genuinely blocking ──────────────────────────────────────────────────

    @Test
    @DisplayName("a gated call blocks until the developer approves, then proceeds")
    void gatedCallBlocksUntilApproved() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(30, "observe");

        Future<ApprovalService.Decision> held = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));

        String approvalId = awaitPendingId(service);
        assertTrue(service.decide(approvalId, true, "looks fine"));

        ApprovalService.Decision decision = held.get(10, TimeUnit.SECONDS);
        assertTrue(decision.allowed());
        assertEquals(Approval.APPROVED, decision.status());
        assertEquals("looks fine", decision.reason());
        assertEquals(Approval.APPROVED, repository.findById(approvalId).orElseThrow().status());
    }

    @Test
    @DisplayName("denying tells the agent why rather than failing silently")
    void deniedCallCarriesReason() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(30, "observe");

        Future<ApprovalService.Decision> held = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));
        String approvalId = awaitPendingId(service);
        service.decide(approvalId, false, "not on production");

        ApprovalService.Decision decision = held.get(10, TimeUnit.SECONDS);
        assertFalse(decision.allowed());
        assertEquals(Approval.DENIED, decision.status());
        assertEquals("not on production", decision.reason());
    }

    @Test
    @DisplayName("silence denies — an absent developer must never mean yes")
    void timeoutDenies() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(1, "observe");

        ApprovalService.Decision decision = pool
                .submit(() -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO))
                .get(10, TimeUnit.SECONDS);

        assertFalse(decision.allowed());
        assertEquals(Approval.TIMED_OUT, decision.status());
        assertTrue(decision.reason().toLowerCase().contains("timed out"));
        assertTrue(service.pending().isEmpty(), "the expired card is cleared");
    }

    @Test
    @DisplayName("the risk band is recorded so the UI can colour the card")
    void recordsRiskBand() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(1, "observe");
        pool.submit(() -> service.evaluate(CLAUDE_SESSION, "Bash",
                Json.read("{\"command\":\"rm -rf /tmp/x\"}"))).get(10, TimeUnit.SECONDS);

        Approval recorded = repository.rows.values().iterator().next();
        assertEquals("destructive", recorded.risk());
    }

    // ── de-duplication of Claude Code's hook retries ────────────────────────

    @Test
    @DisplayName("a retry arriving while the original is held joins it — one card, one click")
    void duplicateJoinsTheInFlightGate() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(30, "observe");

        Future<ApprovalService.Decision> first = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));
        String approvalId = awaitPendingId(service);

        // Claude Code re-fires the same call a moment later with a new tool_use id.
        Future<ApprovalService.Decision> retry = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));

        Thread.sleep(150);
        assertEquals(1, service.pending().size(), "the retry must not raise a second card");

        service.decide(approvalId, true, "ok");

        assertTrue(first.get(10, TimeUnit.SECONDS).allowed());
        assertTrue(retry.get(10, TimeUnit.SECONDS).allowed(), "one decision releases both");
    }

    @Test
    @DisplayName("a decision keeps applying to an identical call made just afterwards")
    void decisionReplaysOntoLaterRetry() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(30, "observe");

        Future<ApprovalService.Decision> held = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));
        service.decide(awaitPendingId(service), true, "ok");
        held.get(10, TimeUnit.SECONDS);

        // A retry after the gate closed is answered from memory, not re-asked.
        long start = System.currentTimeMillis();
        ApprovalService.Decision replayed = service.evaluate(CLAUDE_SESSION, "Bash", ECHO);

        assertTrue(replayed.allowed());
        assertTrue(System.currentTimeMillis() - start < 1000, "must not block again");
        assertTrue(service.pending().isEmpty());
    }

    @Test
    @DisplayName("a denial replays too, so a rejected command cannot slip through on retry")
    void denialAlsoReplays() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(30, "observe");

        Future<ApprovalService.Decision> held = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));
        service.decide(awaitPendingId(service), false, "no");
        held.get(10, TimeUnit.SECONDS);

        assertFalse(service.evaluate(CLAUDE_SESSION, "Bash", ECHO).allowed());
    }

    @Test
    @DisplayName("a different command is decided on its own merits, never inherited")
    void differentCommandIsNotReplayed() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(1, "observe");

        Future<ApprovalService.Decision> held = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));
        service.decide(awaitPendingId(service), true, "ok");
        held.get(10, TimeUnit.SECONDS);

        // Approving "echo hi" must not approve "rm -rf /" — it is held and expires.
        ApprovalService.Decision other = pool.submit(() -> service.evaluate(
                CLAUDE_SESSION, "Bash", Json.read("{\"command\":\"rm -rf /\"}")))
                .get(10, TimeUnit.SECONDS);

        assertFalse(other.allowed());
        assertEquals(Approval.TIMED_OUT, other.status());
    }

    // ── decision bookkeeping ────────────────────────────────────────────────

    @Test
    @DisplayName("deciding an approval nobody is waiting on reports undelivered")
    void decidingAnExpiredApprovalIsNotDelivered() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(1, "observe");

        pool.submit(() -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO)).get(10, TimeUnit.SECONDS);
        String approvalId = repository.rows.keySet().iterator().next();

        assertFalse(service.decide(approvalId, true, "too late"),
                "the hook already gave up; the UI must be told the click did not reach the agent");
    }

    @Test
    @DisplayName("an unknown or already-decided approval id is rejected")
    void rejectsUnknownOrRepeatDecisions() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(30, "observe");

        assertFalse(service.decide("no-such-id", true, null));

        Future<ApprovalService.Decision> held = pool.submit(
                () -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO));
        String approvalId = awaitPendingId(service);
        assertTrue(service.decide(approvalId, true, "ok"));
        held.get(10, TimeUnit.SECONDS);

        assertFalse(service.decide(approvalId, false, "changed my mind"),
                "a call already released cannot be un-approved");
    }

    @Test
    @DisplayName("a held call parks the session in WAITING_APPROVAL and restores it after")
    void movesSessionThroughWaitingState() throws Exception {
        owned(session(false, "default"));
        ApprovalService service = service(1, "observe");
        pool.submit(() -> service.evaluate(CLAUDE_SESSION, "Bash", ECHO)).get(10, TimeUnit.SECONDS);

        // Once on the way in, once on the way out.
        org.mockito.Mockito.verify(sessions, org.mockito.Mockito.atLeast(2))
                .mutate(anyString(), any());
    }

    /** Waits for the gate to publish a card rather than sleeping a fixed amount. */
    private String awaitPendingId(ApprovalService service) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<Approval> pending = service.pending();
            if (!pending.isEmpty()) {
                return pending.get(0).id();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no approval became pending");
    }

    @SuppressWarnings("unused")
    private static final CountDownLatch UNUSED = new CountDownLatch(0);
}
