package eu.nexuslayer.acc.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import eu.nexuslayer.acc.model.AgentEvent;
import eu.nexuslayer.acc.model.EventType;

@Repository
public class EventRepository {

    private static final RowMapper<AgentEvent> MAPPER = (rs, i) -> new AgentEvent(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getLong("seq"),
            rs.getLong("ts"),
            EventType.valueOf(rs.getString("type")),
            rs.getString("parent_id"),
            rs.getString("tool_use_id"),
            rs.getString("tool_name"),
            rs.getString("title"),
            rs.getString("status"),
            Nullable.asLong(rs, "duration_ms"),
            rs.getString("payload"));

    private final JdbcTemplate jdbc;

    public EventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(AgentEvent e) {
        jdbc.update("""
                INSERT INTO events (id, session_id, seq, ts, type, parent_id, tool_use_id, tool_name,
                                    title, status, duration_ms, payload)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    duration_ms = excluded.duration_ms,
                    payload = excluded.payload,
                    title = excluded.title
                """,
                e.id(), e.sessionId(), e.seq(), e.ts(), e.type().name(), e.parentId(), e.toolUseId(),
                e.toolName(), e.title(), e.status(), e.durationMs(), e.payload());
    }

    public List<AgentEvent> findBySession(String sessionId) {
        return jdbc.query("SELECT * FROM events WHERE session_id = ? ORDER BY seq ASC", MAPPER, sessionId);
    }

    public Optional<AgentEvent> findByToolUseId(String sessionId, String toolUseId) {
        if (toolUseId == null) {
            return Optional.empty();
        }
        return jdbc.query("SELECT * FROM events WHERE session_id = ? AND tool_use_id = ? AND type = 'TOOL_CALL' LIMIT 1",
                MAPPER, sessionId, toolUseId).stream().findFirst();
    }

    public long nextSeq(String sessionId) {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(seq), 0) FROM events WHERE session_id = ?",
                Long.class, sessionId);
        return (max == null ? 0 : max) + 1;
    }
}
