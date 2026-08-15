package eu.nexuslayer.acc.stats;

import java.time.ZoneId;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    /**
     * @param tz the browser's IANA zone, so "sessions per day" buckets match the
     *           days the developer actually worked rather than the server's UTC.
     */
    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) String tz) {
        return stats.overview(resolveZone(tz));
    }

    private ZoneId resolveZone(String tz) {
        if (tz == null || tz.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            // A bogus zone from a query string must not 500 the dashboard.
            return ZoneId.systemDefault();
        }
    }
}
