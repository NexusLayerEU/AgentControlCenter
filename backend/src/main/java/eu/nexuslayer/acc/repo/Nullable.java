package eu.nexuslayer.acc.repo;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQLite is dynamically typed and hands back whatever boxed type fits the stored
 * value, so a nullable INTEGER column can arrive as Integer or Long depending on
 * magnitude. These helpers read through Number rather than casting blindly.
 */
final class Nullable {

    private Nullable() {
    }

    static Long asLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    static Integer asInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    static Double asDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).doubleValue();
    }
}
