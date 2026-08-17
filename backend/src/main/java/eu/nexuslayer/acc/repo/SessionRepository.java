package eu.nexuslayer.acc.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import eu.nexuslayer.acc.model.AgentSession;
import eu.nexuslayer.acc.model.SessionStatus;

@Repository
public class SessionRepository {

    private static final RowMapper<AgentSession> MAPPER = (rs, i) -> new AgentSession(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("cwd"),
            rs.getString("prompt"),
            rs.getString("model"),
            rs.getString("permission_mode"),
            rs.getInt("auto_approve") == 1,
            SessionStatus.valueOf(rs.getString("status")),
            rs.getString("origin"),
            rs.getString("claude_session_id"),
            Nullable.asLong(rs, "pid"),
            rs.getLong("created_at"),
            rs.getLong("updated_at"),
            Nullable.asLong(rs, "ended_at"),
            Nullable.asInt(rs, "exit_code"),
            rs.getString("result_text"),
            Nullable.asDouble(rs, "total_cost_usd"),
            Nullable.asInt(rs, "num_turns"),
            Nullable.asLong(rs, "duration_ms"));

    private final JdbcTemplate jdbc;

    public SessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(AgentSession s) {
        jdbc.update("""
                INSERT INTO sessions (id, name, cwd, prompt, model, permission_mode, auto_approve, status,
                                      origin, claude_session_id, pid, created_at, updated_at, ended_at,
                                      exit_code, result_text, total_cost_usd, num_turns, duration_ms)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    prompt = excluded.prompt,
                    model = excluded.model,
                    permission_mode = excluded.permission_mode,
                    auto_approve = excluded.auto_approve,
                    status = excluded.status,
                    claude_session_id = excluded.claude_session_id,
                    pid = excluded.pid,
                    updated_at = excluded.updated_at,
                    ended_at = excluded.ended_at,
                    exit_code = excluded.exit_code,
                    result_text = excluded.result_text,
                    total_cost_usd = excluded.total_cost_usd,
                    num_turns = excluded.num_turns,
                    duration_ms = excluded.duration_ms
                """,
                s.id(), s.name(), s.cwd(), s.prompt(), s.model(), s.permissionMode(), s.autoApprove() ? 1 : 0,
                s.status().name(), s.origin(), s.claudeSessionId(), s.pid(), s.createdAt(), s.updatedAt(),
                s.endedAt(), s.exitCode(), s.resultText(), s.totalCostUsd(), s.numTurns(), s.durationMs());
    }

    public Optional<AgentSession> findById(String id) {
        return jdbc.query("SELECT * FROM sessions WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<AgentSession> findByClaudeSessionId(String claudeSessionId) {
        if (claudeSessionId == null) {
            return Optional.empty();
        }
        return jdbc.query("SELECT * FROM sessions WHERE claude_session_id = ? ORDER BY created_at DESC LIMIT 1",
                MAPPER, claudeSessionId).stream().findFirst();
    }

    public List<AgentSession> findAll(int limit) {
        return jdbc.query("SELECT * FROM sessions ORDER BY created_at DESC LIMIT ?", MAPPER, limit);
    }

    public void delete(String id) {
        jdbc.update("DELETE FROM events WHERE session_id = ?", id);
        jdbc.update("DELETE FROM approvals WHERE session_id = ?", id);
        // Without this the cursor outlives the session, so a re-adopted Claude
        // session would resume mid-file instead of from the start.
        jdbc.update("DELETE FROM transcript_cursors WHERE claude_session_id = "
                + "(SELECT claude_session_id FROM sessions WHERE id = ?)", id);
        jdbc.update("DELETE FROM sessions WHERE id = ?", id);
    }

    /**
     * Adopted sessions still marked live but untouched since {@code cutoff}. The
     * window they belong to is almost certainly closed — Claude Code does not tell
     * us when that happens.
     */
    public List<AgentSession> findStaleAdopted(long cutoff) {
        return jdbc.query(
                "SELECT * FROM sessions WHERE origin = 'hook' "
                        + "AND status IN ('IDLE','RUNNING','STARTING') AND updated_at < ?",
                MAPPER, cutoff);
    }

    /** Finished sessions old enough that nothing more will arrive for them. */
    public List<AgentSession> findRecentlyFinished(long cutoff) {
        return jdbc.query(
                "SELECT * FROM sessions WHERE status IN ('COMPLETED','FAILED','CANCELLED') "
                        + "AND updated_at < ? AND updated_at > ?",
                MAPPER, cutoff, cutoff - 3_600_000);
    }

    /**
     * A daemon restart leaves orphaned rows claiming to be running. Nothing is
     * attached to those processes any more, so mark them failed on boot.
     */
    public int markOrphansFailed() {
        long now = System.currentTimeMillis();
        // A dispatched agent really did die with the daemon — that is a failure.
        int failed = jdbc.update(
                "UPDATE sessions SET status = 'FAILED', ended_at = ?, updated_at = ? "
                        + "WHERE origin <> 'hook' AND status IN ('STARTING','RUNNING','WAITING_APPROVAL')",
                now, now);
        // An adopted session is somebody's terminal. ACC merely stopped watching
        // it; calling that FAILED blames the user's window for our restart.
        int closed = jdbc.update(
                "UPDATE sessions SET status = 'COMPLETED', ended_at = ?, updated_at = ? "
                        + "WHERE origin = 'hook' AND status IN ('STARTING','RUNNING','WAITING_APPROVAL','IDLE')",
                now, now);
        return failed + closed;
    }
}
