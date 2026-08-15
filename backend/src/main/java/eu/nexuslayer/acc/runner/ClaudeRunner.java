package eu.nexuslayer.acc.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.AccPaths;
import eu.nexuslayer.acc.config.AccProperties;
import eu.nexuslayer.acc.event.EventService;
import eu.nexuslayer.acc.model.EventType;
import eu.nexuslayer.acc.util.Json;

/**
 * Spawns {@code claude} headless with structured JSON output and feeds every
 * line to {@link StreamJsonParser}.
 *
 * <p>Headless mode rather than a PTY is deliberate: the interactive TUI paints
 * ANSI that cannot be parsed reliably, whereas {@code --output-format stream-json}
 * gives exact tool calls, inputs and results — which is what the tree and graph
 * views are built from. The raw terminal experience is served separately by
 * {@link eu.nexuslayer.acc.pty.PtyRegistry}.
 */
@Component
public class ClaudeRunner {

    private static final Logger log = LoggerFactory.getLogger(ClaudeRunner.class);

    private final AccProperties properties;
    private final EventService events;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "acc-runner");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Process> running = new ConcurrentHashMap<>();

    public ClaudeRunner(AccProperties properties, EventService events) {
        this.properties = properties;
        this.events = events;
    }

    public interface Callbacks {
        void onStarted(long pid);

        void onClaudeSessionId(String claudeSessionId);

        void onResult(JsonNode resultNode);

        void onExit(int exitCode);
    }

    public void start(String sessionId, StartSessionRequest request, Callbacks callbacks) {
        pool.submit(() -> run(sessionId, request, callbacks));
    }

    private void run(String sessionId, StartSessionRequest request, Callbacks callbacks) {
        List<String> command = buildCommand(request);
        log.info("Session {} launching: {}", sessionId, String.join(" ", command));

        Path errorLog = AccPaths.home().resolve("logs").resolve(sessionId + ".stderr.log");
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(new File(request.cwd()))
                    .redirectErrorStream(false)
                    .redirectError(errorLog.toFile());
            builder.environment().put("ACC_SESSION_ID", sessionId);
            builder.environment().put("ACC_DAEMON_URL", "http://127.0.0.1:4000");

            Process process = builder.start();
            running.put(sessionId, process);
            callbacks.onStarted(process.pid());

            StreamJsonParser parser = new StreamJsonParser(
                    sessionId,
                    request.cwd(),
                    events,
                    init -> callbacks.onClaudeSessionId(Json.text(init, "session_id")),
                    callbacks::onResult);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parser.accept(line);
                }
            }

            int exit = process.waitFor();
            running.remove(sessionId);
            if (exit != 0) {
                events.record(sessionId, EventType.ERROR, "Agent exited with code " + exit,
                        Json.write(Map.of("exitCode", exit, "stderr", tail(errorLog))));
            }
            callbacks.onExit(exit);

        } catch (IOException e) {
            running.remove(sessionId);
            log.error("Session {} failed to launch", sessionId, e);
            events.record(sessionId, EventType.ERROR, "Failed to launch agent",
                    Json.write(Map.of("message", String.valueOf(e.getMessage()),
                            "command", String.join(" ", command))));
            callbacks.onExit(-1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.remove(sessionId);
            callbacks.onExit(-1);
        }
    }

    /** Package-private so the argument mapping can be asserted without spawning a process. */
    List<String> buildCommand(StartSessionRequest request) {
        List<String> command = new ArrayList<>();
        command.add(ClaudeLauncher.resolve(properties.claudeBinary()));
        command.add("-p");
        command.add(request.prompt());
        command.add("--output-format");
        command.add("stream-json");
        command.add("--verbose");

        String mode = request.resolvedMode();
        if (StartSessionRequest.MODE_BYPASS.equals(mode)) {
            command.add("--dangerously-skip-permissions");
        } else if (!StartSessionRequest.MODE_DEFAULT.equals(mode)) {
            command.add("--permission-mode");
            command.add(mode);
        }

        if (request.model() != null && !request.model().isBlank()) {
            command.add("--model");
            command.add(request.model());
        }
        return command;
    }

    public boolean cancel(String sessionId) {
        Process process = running.remove(sessionId);
        if (process == null) {
            return false;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        return true;
    }

    public boolean isRunning(String sessionId) {
        Process process = running.get(sessionId);
        return process != null && process.isAlive();
    }

    private String tail(Path file) {
        try {
            if (!Files.exists(file)) {
                return "";
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - 20);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (IOException e) {
            return "";
        }
    }
}
