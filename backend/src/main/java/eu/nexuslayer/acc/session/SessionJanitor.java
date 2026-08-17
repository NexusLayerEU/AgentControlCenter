package eu.nexuslayer.acc.session;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.hooks.AdoptionService;
import eu.nexuslayer.acc.hooks.TranscriptReader;
import eu.nexuslayer.acc.model.AgentSession;
import eu.nexuslayer.acc.model.SessionStatus;
import eu.nexuslayer.acc.repo.SessionRepository;
import eu.nexuslayer.acc.ws.Broadcaster;

/**
 * Closes the books on sessions nobody is driving any more.
 *
 * <p>An adopted session goes IDLE when a turn ends, and nothing ever moves it on:
 * Claude Code does not tell us when you close the window. Left alone, every
 * terminal you have ever opened stays "active" forever and the dashboard's active
 * count becomes meaningless — observed at four sessions still listed as live two
 * days after their windows were shut.
 *
 * <p>This also evicts the per-session state the daemon keeps in memory. It is a
 * long-running background process; anything keyed by session id and never removed
 * is a slow leak.
 */
@Component
public class SessionJanitor {

    private static final Logger log = LoggerFactory.getLogger(SessionJanitor.class);

    private final SessionRepository sessions;
    private final EventService events;
    private final AdoptionService adoption;
    private final TranscriptReader transcripts;
    private final Broadcaster broadcaster;
    private final JdbcTemplate jdbc;
    private final long staleAfterMillis;

    public SessionJanitor(SessionRepository sessions, EventService events, AdoptionService adoption,
            TranscriptReader transcripts, Broadcaster broadcaster, JdbcTemplate jdbc,
            @Value("${acc.stale-session-minutes:30}") long staleMinutes) {
        this.sessions = sessions;
        this.events = events;
        this.adoption = adoption;
        this.transcripts = transcripts;
        this.broadcaster = broadcaster;
        this.jdbc = jdbc;
        this.staleAfterMillis = TimeUnit.MINUTES.toMillis(staleMinutes);
    }

    /** Runs a couple of minutes after boot, then every five minutes by default. */
    @Scheduled(initialDelayString = "${acc.sweep-initial-delay-ms:120000}",
               fixedDelayString = "${acc.sweep-interval-ms:300000}")
    public void sweep() {
        long cutoff = System.currentTimeMillis() - staleAfterMillis;

        List<AgentSession> stale = sessions.findStaleAdopted(cutoff);
        for (AgentSession session : stale) {
            AgentSession closed = session.completed(SessionStatus.COMPLETED, null,
                    session.resultText(), session.totalCostUsd(), session.numTurns(),
                    session.updatedAt() - session.createdAt());
            sessions.save(closed);
            broadcaster.broadcast("session", closed);
            release(session);
            log.info("Closed adopted session '{}' — no activity for over {} minutes",
                    session.name(), TimeUnit.MILLISECONDS.toMinutes(staleAfterMillis));
        }

        // Sessions that ended normally also leave their in-memory state behind.
        for (AgentSession finished : sessions.findRecentlyFinished(cutoff)) {
            release(finished);
        }

        int orphans = jdbc.update("""
                DELETE FROM transcript_cursors
                WHERE claude_session_id NOT IN (
                    SELECT claude_session_id FROM sessions WHERE claude_session_id IS NOT NULL)
                """);
        if (orphans > 0) {
            log.debug("Removed {} orphaned transcript cursor(s)", orphans);
        }
    }

    /** Drops everything the daemon was holding for this session. */
    public void release(AgentSession session) {
        events.forget(session.id());
        adoption.forget(session.claudeSessionId());
        transcripts.forget(session.claudeSessionId());
    }
}
