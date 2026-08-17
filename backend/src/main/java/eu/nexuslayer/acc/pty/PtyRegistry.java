package eu.nexuslayer.acc.pty;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import eu.nexuslayer.acc.ws.Broadcaster;

/**
 * Optional raw-terminal side of ACC: a real PTY per terminal pane, streamed to
 * xterm.js. Kept independent of {@link eu.nexuslayer.acc.runner.ClaudeRunner} so
 * a native pty4j failure degrades the terminal feature without taking down
 * structured session tracking.
 */
@Component
public class PtyRegistry {

    private static final Logger log = LoggerFactory.getLogger(PtyRegistry.class);

    private final Broadcaster broadcaster;
    private final Map<String, PtyProcess> terminals = new ConcurrentHashMap<>();
    private final ExecutorService readers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "acc-pty");
        t.setDaemon(true);
        return t;
    });

    public PtyRegistry(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    public boolean open(String terminalId, String cwd, String[] command, int cols, int rows) {
        if (terminals.containsKey(terminalId)) {
            return true;
        }
        try {
            Map<String, String> env = new HashMap<>(System.getenv());
            env.put("TERM", "xterm-256color");
            env.put("COLORTERM", "truecolor");
            env.put("ACC_TERMINAL_ID", terminalId);

            PtyProcess process = new PtyProcessBuilder(command)
                    .setDirectory(cwd)
                    .setEnvironment(env)
                    .setInitialColumns(cols > 0 ? cols : 120)
                    .setInitialRows(rows > 0 ? rows : 32)
                    .start();

            terminals.put(terminalId, process);
            readers.submit(() -> pump(terminalId, process));
            log.info("PTY {} opened in {}", terminalId, cwd);
            return true;
        } catch (Exception e) {
            log.error("Unable to open PTY {}: {}", terminalId, e.getMessage());
            broadcaster.broadcast("pty:error",
                    Map.of("sessionId", terminalId, "message", String.valueOf(e.getMessage())));
            return false;
        }
    }

    private void pump(String terminalId, PtyProcess process) {
        char[] buffer = new char[8192];
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                broadcaster.broadcast("pty:data",
                        Map.of("sessionId", terminalId, "data", new String(buffer, 0, read)));
            }
        } catch (IOException e) {
            log.debug("PTY {} read ended: {}", terminalId, e.getMessage());
        } finally {
            terminals.remove(terminalId);
            broadcaster.broadcast("pty:exit", Map.of("sessionId", terminalId));
        }
    }

    public void write(String terminalId, String data) {
        PtyProcess process = terminals.get(terminalId);
        if (process == null || data == null) {
            return;
        }
        try {
            process.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
        } catch (IOException e) {
            log.debug("PTY {} write failed: {}", terminalId, e.getMessage());
        }
    }

    public void resize(String terminalId, int cols, int rows) {
        PtyProcess process = terminals.get(terminalId);
        if (process != null && cols > 0 && rows > 0) {
            process.setWinSize(new WinSize(cols, rows));
        }
    }

    public void close(String terminalId) {
        PtyProcess process = terminals.remove(terminalId);
        if (process != null) {
            process.destroy();
        }
    }

    /**
     * Closes every terminal.
     *
     * <p>A PTY exists only to feed a browser pane. If the last dashboard goes away
     * the shells behind those panes have nobody to talk to, and nothing would ever
     * reap them — closing the tab used to leave a live shell per terminal opened,
     * for the lifetime of the daemon.
     */
    public int closeAll() {
        int n = terminals.size();
        terminals.keySet().forEach(this::close);
        return n;
    }

    public boolean isOpen(String terminalId) {
        PtyProcess process = terminals.get(terminalId);
        return process != null && process.isAlive();
    }
}
