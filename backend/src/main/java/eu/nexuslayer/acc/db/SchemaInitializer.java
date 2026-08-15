package eu.nexuslayer.acc.db;

import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Applies the SQLite schema and the pragmas ACC depends on. WAL plus a busy
 * timeout keeps the hook endpoint (which writes while the PTY reader is also
 * writing) from tripping over SQLITE_BUSY.
 */
@Component
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcTemplate jdbc;

    public SchemaInitializer(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        try {
            jdbc.execute("PRAGMA journal_mode=WAL");
            jdbc.execute("PRAGMA busy_timeout=5000");
            jdbc.execute("PRAGMA synchronous=NORMAL");
            jdbc.execute("PRAGMA foreign_keys=ON");

            String sql = new String(new ClassPathResource("schema.sql").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbc.execute(trimmed);
                }
            }
            log.info("ACC schema ready");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize ACC database schema", e);
        }
    }
}
