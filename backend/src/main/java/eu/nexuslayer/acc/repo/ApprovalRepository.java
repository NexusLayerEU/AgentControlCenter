package eu.nexuslayer.acc.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import eu.nexuslayer.acc.model.Approval;

@Repository
public class ApprovalRepository {

    private static final RowMapper<Approval> MAPPER = (rs, i) -> new Approval(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getString("claude_session_id"),
            rs.getString("hook_event"),
            rs.getString("tool_name"),
            rs.getString("tool_input"),
            rs.getString("risk"),
            rs.getString("status"),
            rs.getString("reason"),
            rs.getLong("created_at"),
            Nullable.asLong(rs, "decided_at"));

    private final JdbcTemplate jdbc;

    public ApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Approval a) {
        jdbc.update("""
                INSERT INTO approvals (id, session_id, claude_session_id, hook_event, tool_name, tool_input,
                                       risk, status, reason, created_at, decided_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    reason = excluded.reason,
                    decided_at = excluded.decided_at
                """,
                a.id(), a.sessionId(), a.claudeSessionId(), a.hookEvent(), a.toolName(), a.toolInput(),
                a.risk(), a.status(), a.reason(), a.createdAt(), a.decidedAt());
    }

    public Optional<Approval> findById(String id) {
        return jdbc.query("SELECT * FROM approvals WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Approval> findPending() {
        return jdbc.query("SELECT * FROM approvals WHERE status = 'pending' ORDER BY created_at ASC", MAPPER);
    }

    public List<Approval> findBySession(String sessionId) {
        return jdbc.query("SELECT * FROM approvals WHERE session_id = ? ORDER BY created_at DESC", MAPPER,
                sessionId);
    }

    public int expireAllPending() {
        return jdbc.update("UPDATE approvals SET status = 'timed_out', reason = 'daemon restarted', decided_at = ? "
                + "WHERE status = 'pending'", System.currentTimeMillis());
    }
}
