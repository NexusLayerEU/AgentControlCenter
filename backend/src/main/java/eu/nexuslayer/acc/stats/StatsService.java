package eu.nexuslayer.acc.stats;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Aggregates the overview dashboard's numbers in SQL rather than by pulling every
 * session's timeline over the wire. Risk lives inside each TOOL_CALL's JSON
 * payload, which SQLite's JSON1 extension can read directly.
 */
@Service
public class StatsService {

    /** Days of history in the activity chart. */
    private static final int ACTIVITY_DAYS = 14;
    /** Bars in the tool chart before the tail folds into "other". */
    private static final int TOOL_SLOTS = 8;

    private final JdbcTemplate jdbc;

    public StatsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> overview(ZoneId zone) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessions", sessionCounts());
        out.put("totals", totals());
        out.put("activity", activity(zone));
        out.put("tools", tools());
        out.put("risk", risk());
        out.put("approvals", approvals());
        out.put("modes", modes());
        out.put("generatedAt", System.currentTimeMillis());
        return out;
    }

    private Map<String, Object> sessionCounts() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS n FROM sessions GROUP BY status",
                rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("n"));
                });

        long running = byStatus.getOrDefault("RUNNING", 0L);
        long starting = byStatus.getOrDefault("STARTING", 0L);
        long waiting = byStatus.getOrDefault("WAITING_APPROVAL", 0L);
        long idle = byStatus.getOrDefault("IDLE", 0L);
        long completed = byStatus.getOrDefault("COMPLETED", 0L);
        long failed = byStatus.getOrDefault("FAILED", 0L);
        long cancelled = byStatus.getOrDefault("CANCELLED", 0L);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("running", running);
        out.put("starting", starting);
        out.put("waitingApproval", waiting);
        out.put("idle", idle);
        out.put("completed", completed);
        out.put("failed", failed);
        out.put("cancelled", cancelled);
        // An adopted window sitting between turns is still open, so it counts as live.
        out.put("active", running + starting + waiting + idle);
        out.put("history", completed + failed + cancelled);
        out.put("total", running + starting + waiting + idle + completed + failed + cancelled);
        return out;
    }

    private Map<String, Object> totals() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("toolCalls", one("SELECT COUNT(*) FROM events WHERE type = 'TOOL_CALL'"));
        out.put("toolErrors", one("SELECT COUNT(*) FROM events WHERE type = 'TOOL_CALL' AND status = 'error'"));
        out.put("events", one("SELECT COUNT(*) FROM events"));
        out.put("costUsd", oneDouble("SELECT COALESCE(SUM(total_cost_usd), 0) FROM sessions"));
        out.put("turns", one("SELECT COALESCE(SUM(num_turns), 0) FROM sessions"));
        out.put("agentMs", one("SELECT COALESCE(SUM(duration_ms), 0) FROM sessions"));
        // Median would be better than mean for a long tail, but SQLite has no
        // percentile function and the value is only ever shown as a rough figure.
        out.put("avgSessionMs", one(
                "SELECT COALESCE(CAST(AVG(duration_ms) AS INTEGER), 0) FROM sessions WHERE duration_ms IS NOT NULL"));
        return out;
    }

    /** One row per day for the last {@value #ACTIVITY_DAYS} days, zero-filled. */
    private List<Map<String, Object>> activity(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(ACTIVITY_DAYS - 1L);
        long fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli();

        Map<String, long[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < ACTIVITY_DAYS; i++) {
            byDay.put(from.plusDays(i).toString(), new long[] { 0, 0 });
        }

        jdbc.query("SELECT created_at FROM sessions WHERE created_at >= ?", rs -> {
            String day = dayOf(rs.getLong(1), zone);
            long[] slot = byDay.get(day);
            if (slot != null) {
                slot[0]++;
            }
        }, fromMillis);

        jdbc.query("SELECT ts FROM events WHERE type = 'TOOL_CALL' AND ts >= ?", rs -> {
            String day = dayOf(rs.getLong(1), zone);
            long[] slot = byDay.get(day);
            if (slot != null) {
                slot[1]++;
            }
        }, fromMillis);

        List<Map<String, Object>> out = new ArrayList<>();
        byDay.forEach((day, slot) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day);
            row.put("sessions", slot[0]);
            row.put("toolCalls", slot[1]);
            out.add(row);
        });
        return out;
    }

    private String dayOf(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString();
    }

    /** Most-used tools, with the tail folded into a single "other" bar. */
    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT tool_name AS name,
                       COUNT(*) AS calls,
                       SUM(CASE WHEN status = 'error' THEN 1 ELSE 0 END) AS errors,
                       COALESCE(CAST(AVG(duration_ms) AS INTEGER), 0) AS avg_ms
                FROM events
                WHERE type = 'TOOL_CALL' AND tool_name IS NOT NULL
                GROUP BY tool_name
                ORDER BY calls DESC
                """, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", rs.getString("name"));
            row.put("calls", rs.getLong("calls"));
            row.put("errors", rs.getLong("errors"));
            row.put("avgMs", rs.getLong("avg_ms"));
            return row;
        });

        if (rows.size() <= TOOL_SLOTS) {
            return rows;
        }
        List<Map<String, Object>> head = new ArrayList<>(rows.subList(0, TOOL_SLOTS - 1));
        long calls = 0;
        long errors = 0;
        for (Map<String, Object> row : rows.subList(TOOL_SLOTS - 1, rows.size())) {
            calls += (Long) row.get("calls");
            errors += (Long) row.get("errors");
        }
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("name", "other");
        other.put("calls", calls);
        other.put("errors", errors);
        other.put("avgMs", 0L);
        head.add(other);
        return head;
    }

    private Map<String, Object> risk() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String band : List.of("safe", "normal", "elevated", "destructive")) {
            out.put(band, one(
                    "SELECT COUNT(*) FROM events WHERE type = 'TOOL_CALL' "
                            + "AND json_extract(payload, '$.risk') = ?", band));
        }
        return out;
    }

    private Map<String, Object> approvals() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        // Braces matter: a value-returning lambda makes the query() overload ambiguous.
        jdbc.query("SELECT status, COUNT(*) AS n FROM approvals GROUP BY status", rs -> {
            byStatus.put(rs.getString("status"), rs.getLong("n"));
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pending", byStatus.getOrDefault("pending", 0L));
        out.put("approved", byStatus.getOrDefault("approved", 0L));
        out.put("denied", byStatus.getOrDefault("denied", 0L));
        out.put("timedOut", byStatus.getOrDefault("timed_out", 0L));
        out.put("autoApproved", byStatus.getOrDefault("auto_approved", 0L));
        return out;
    }

    /** How many runs were gated versus launched to run unattended. */
    private Map<String, Object> modes() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gated", one("SELECT COUNT(*) FROM sessions WHERE auto_approve = 0"));
        out.put("unattended", one("SELECT COUNT(*) FROM sessions WHERE auto_approve = 1"));
        return out;
    }

    private long one(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private double oneDouble(String sql, Object... args) {
        Double value = jdbc.queryForObject(sql, Double.class, args);
        return value == null ? 0d : value;
    }

    /** Exposed for the controller's cache header. */
    public static Duration freshness() {
        return Duration.ofSeconds(5);
    }
}
