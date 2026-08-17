package eu.nexuslayer.acc.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.pty.PtyRegistry;
import eu.nexuslayer.acc.util.Json;

/**
 * Inbound side of the dashboard socket: connection bookkeeping plus routing
 * terminal keystrokes and resizes into the PTY registry. Outbound fan-out lives
 * in {@link SocketHub}.
 */
@Component
public class AccWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AccWebSocketHandler.class);

    private final SocketHub hub;
    private final PtyRegistry ptyRegistry;

    public AccWebSocketHandler(SocketHub hub, PtyRegistry ptyRegistry) {
        this.hub = hub;
        this.ptyRegistry = ptyRegistry;
        hub.whenLastClientDisconnects(() -> {
            int closed = ptyRegistry.closeAll();
            if (closed > 0) {
                log.info("Closed {} terminal(s): no dashboard left to serve them", closed);
            }
        });
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode node = Json.read(message.getPayload());
        String channel = Json.text(node, "channel");
        if (channel == null) {
            return;
        }
        switch (channel) {
            case "pty:input" -> ptyRegistry.write(Json.text(node, "sessionId"), Json.text(node, "data"));
            case "pty:resize" -> {
                JsonNode cols = node.get("cols");
                JsonNode rows = node.get("rows");
                if (cols != null && rows != null) {
                    ptyRegistry.resize(Json.text(node, "sessionId"), cols.asInt(), rows.asInt());
                }
            }
            default -> {
                // Unknown inbound channels are ignored rather than erroring the socket.
            }
        }
    }
}
