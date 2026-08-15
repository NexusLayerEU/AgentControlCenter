package eu.nexuslayer.acc.ws;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import eu.nexuslayer.acc.util.Json;

/**
 * Owns the set of connected dashboards and the outbound fan-out.
 *
 * <p>Deliberately separate from {@link AccWebSocketHandler}: the handler also
 * routes inbound terminal keystrokes into the PTY registry, and the registry
 * needs to broadcast. Keeping outbound here means neither side depends on the
 * other.
 */
@Component
public class SocketHub implements Broadcaster {

    private static final Logger log = LoggerFactory.getLogger(SocketHub.class);

    private final Set<WebSocketSession> clients = ConcurrentHashMap.newKeySet();

    public void register(WebSocketSession session) {
        clients.add(session);
        send(session, Json.write(Map.of("channel", "hello", "clients", clients.size())));
        log.debug("Dashboard connected ({} total)", clients.size());
    }

    public void unregister(WebSocketSession session) {
        clients.remove(session);
    }

    @Override
    public void broadcast(String channel, Object payload) {
        if (clients.isEmpty()) {
            return;
        }
        String frame = Json.write(Map.of(
                "channel", channel,
                "payload", payload,
                "ts", System.currentTimeMillis()));
        for (WebSocketSession client : clients) {
            send(client, frame);
        }
    }

    private void send(WebSocketSession session, String frame) {
        if (!session.isOpen()) {
            clients.remove(session);
            return;
        }
        try {
            // Tomcat forbids concurrent sends on one session; the events and PTY
            // pump run on different threads, so serialise per client.
            synchronized (session) {
                session.sendMessage(new TextMessage(frame));
            }
        } catch (IOException e) {
            log.debug("Dropping dead websocket client: {}", e.getMessage());
            clients.remove(session);
        }
    }
}
