package eu.nexuslayer.acc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import eu.nexuslayer.acc.ws.AccWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AccWebSocketHandler handler;

    public WebSocketConfig(AccWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Loopback-only server, so a permissive origin check is acceptable here
        // and lets the Vite dev server on :5173 connect during development.
        registry.addHandler(handler, "/ws").setAllowedOriginPatterns("*");
    }
}
