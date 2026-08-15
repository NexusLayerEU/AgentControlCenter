package eu.nexuslayer.acc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The dashboard routes entirely in the URL hash ({@code #/<sessionId>/<view>}),
 * so the daemon needs no SPA fallback — every navigable URL is still {@code /}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Loopback origins only; this covers the Vite dev server on :5173.
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
