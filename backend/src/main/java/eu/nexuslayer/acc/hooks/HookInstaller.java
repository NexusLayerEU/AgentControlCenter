package eu.nexuslayer.acc.hooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.nexuslayer.acc.AccPaths;
import eu.nexuslayer.acc.Platform;
import eu.nexuslayer.acc.util.Json;

/**
 * Writes the bridge script and registers it in Claude Code's settings.json.
 *
 * <p>Existing hooks are preserved: ACC's entries are tagged and only ACC's own
 * entries are replaced on reinstall, so a user's own hooks are never clobbered.
 */
@Service
public class HookInstaller {

    private static final Logger log = LoggerFactory.getLogger(HookInstaller.class);
    private static final String MARKER = "acc-bridge";

    /** Hook event -> daemon endpoint. */
    private static final Map<String, String> HOOKS = Map.of(
            "PreToolUse", "pre-tool-use",
            "PostToolUse", "post-tool-use",
            "Stop", "stop",
            "SessionStart", "session-start",
            // The only reliable "your window closed" signal. Without it an
            // adopted session can only be aged out on a guess.
            "SessionEnd", "session-end",
            "Notification", "notification");

    /** Only PreToolUse can block, so only it needs a long timeout. */
    private static final Set<String> BLOCKING = Set.of("PreToolUse");

    public record InstallResult(String scriptPath, String settingsPath, List<String> installed) {
    }

    public InstallResult install(int port, boolean projectScope, String projectDir) throws IOException {
        Path script = writeScript(port);
        Path settings = projectScope
                ? Path.of(projectDir, ".claude", "settings.json")
                : Path.of(System.getProperty("user.home"), ".claude", "settings.json");

        Files.createDirectories(settings.getParent());
        ObjectNode root = readSettings(settings);
        ObjectNode hooks = root.has("hooks") && root.get("hooks").isObject()
                ? (ObjectNode) root.get("hooks")
                : root.putObject("hooks");

        List<String> installed = new ArrayList<>();
        HOOKS.forEach((event, endpoint) -> {
            hooks.set(event, buildMatchers(hooks.get(event), script, endpoint, event));
            installed.add(event);
        });

        Files.writeString(settings, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
        log.info("Installed ACC hooks into {}", settings);
        return new InstallResult(script.toString(), settings.toString(), installed);
    }

    public InstallResult uninstall(boolean projectScope, String projectDir) throws IOException {
        Path settings = projectScope
                ? Path.of(projectDir, ".claude", "settings.json")
                : Path.of(System.getProperty("user.home"), ".claude", "settings.json");
        if (!Files.exists(settings)) {
            return new InstallResult(null, settings.toString(), List.of());
        }
        ObjectNode root = readSettings(settings);
        List<String> removed = new ArrayList<>();
        if (root.has("hooks") && root.get("hooks").isObject()) {
            ObjectNode hooks = (ObjectNode) root.get("hooks");
            for (String event : HOOKS.keySet()) {
                JsonNode existing = hooks.get(event);
                if (existing == null || !existing.isArray()) {
                    continue;
                }
                var kept = Json.mapper().createArrayNode();
                existing.forEach(matcher -> {
                    if (!isAccMatcher(matcher)) {
                        kept.add(matcher);
                    }
                });
                if (kept.isEmpty()) {
                    hooks.remove(event);
                } else {
                    hooks.set(event, kept);
                }
                removed.add(event);
            }
        }
        Files.writeString(settings, Json.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
        return new InstallResult(null, settings.toString(), removed);
    }

    public boolean isInstalled(boolean projectScope, String projectDir) {
        try {
            Path settings = projectScope
                    ? Path.of(projectDir, ".claude", "settings.json")
                    : Path.of(System.getProperty("user.home"), ".claude", "settings.json");
            if (!Files.exists(settings)) {
                return false;
            }
            JsonNode preToolUse = readSettings(settings).path("hooks").path("PreToolUse");
            if (!preToolUse.isArray()) {
                return false;
            }
            for (JsonNode matcher : preToolUse) {
                if (isAccMatcher(matcher)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private com.fasterxml.jackson.databind.node.ArrayNode buildMatchers(JsonNode existing, Path script,
            String endpoint, String event) {
        var matchers = Json.mapper().createArrayNode();
        if (existing != null && existing.isArray()) {
            existing.forEach(matcher -> {
                if (!isAccMatcher(matcher)) {
                    matchers.add(matcher);
                }
            });
        }

        ObjectNode hook = Json.mapper().createObjectNode();
        hook.put("type", "command");
        hook.put("command", Platform.hookCommand(script.toString(), endpoint));
        hook.put("timeout", BLOCKING.contains(event) ? 60 : 10);

        ObjectNode matcher = Json.mapper().createObjectNode();
        matcher.put("matcher", "*");
        matcher.put("_source", MARKER);
        matcher.putArray("hooks").add(hook);

        matchers.add(matcher);
        return matchers;
    }

    private boolean isAccMatcher(JsonNode matcher) {
        if (MARKER.equals(matcher.path("_source").asText(null))) {
            return true;
        }
        // Fall back to inspecting the command for installs written before the tag existed.
        JsonNode hooks = matcher.path("hooks");
        if (hooks.isArray()) {
            for (JsonNode hook : hooks) {
                String command = hook.path("command").asText("");
                if (command.contains("acc-hook.sh") || command.contains("acc-hook.ps1")) {
                    return true;
                }
            }
        }
        return false;
    }

    private ObjectNode readSettings(Path settings) throws IOException {
        if (!Files.exists(settings)) {
            return Json.mapper().createObjectNode();
        }
        String raw = Files.readString(settings, StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return Json.mapper().createObjectNode();
        }
        JsonNode parsed = Json.read(raw);
        if (!parsed.isObject()) {
            throw new IOException("settings.json at " + settings + " is not a JSON object; refusing to overwrite");
        }
        return (ObjectNode) parsed;
    }

    private Path writeScript(int port) throws IOException {
        Path script = AccPaths.ensureHome().resolve(Platform.hookScriptName());
        Files.writeString(script, Platform.isWindows() ? powershellBridge(port) : bashBridge(port),
                StandardCharsets.UTF_8);
        script.toFile().setExecutable(true, true);
        return script;
    }

    /**
     * Windows bridge. Claude Code cannot exec a .ps1 directly, so the registered
     * command invokes PowerShell explicitly (see {@link Platform#hookCommand}).
     */
    private String powershellBridge(int port) {
        return """
                # Agent Control Center hook bridge - generated, do not edit.
                # Reads Claude Code's hook JSON on stdin, forwards it to the local ACC
                # daemon, and echoes the daemon's response back so ACC can allow or deny.
                param([string]$Endpoint = 'pre-tool-use')

                $payload = [Console]::In.ReadToEnd()
                $uri = 'http://127.0.0.1:%d/hooks/' + $Endpoint

                try {
                    # TimeoutSec stays under Claude Code's own hook timeout so a hung
                    # daemon surfaces as a clean fallback rather than a killed hook.
                    $response = Invoke-RestMethod -Uri $uri -Method Post `
                        -ContentType 'application/json' -Body $payload -TimeoutSec 58
                    if ($null -ne $response) {
                        $response | ConvertTo-Json -Depth 20 -Compress
                    }
                } catch {
                    # Daemon unreachable. Fail open so ACC being down never bricks the
                    # agent; ACC's value is visibility, and a dead dashboard must not
                    # block work.
                }
                exit 0
                """.formatted(port);
    }

    private String bashBridge(int port) {
        return """
                #!/usr/bin/env bash
                # Agent Control Center hook bridge — generated, do not edit.
                # Reads Claude Code's hook JSON on stdin, forwards it to the local ACC
                # daemon, and echoes the daemon's response back so ACC can allow or deny.
                set -uo pipefail

                ENDPOINT="${1:-pre-tool-use}"
                DAEMON="http://127.0.0.1:%d/hooks/${ENDPOINT}"
                PAYLOAD=$(cat)

                # --max-time stays under Claude Code's own hook timeout so a hung daemon
                # surfaces as a clean fallback rather than a killed hook.
                RESPONSE=$(printf '%%s' "$PAYLOAD" \\
                  | curl -sS --max-time 58 \\
                         -H 'Content-Type: application/json' \\
                         --data-binary @- \\
                         "$DAEMON" 2>/dev/null)

                if [ -z "$RESPONSE" ]; then
                  # Daemon unreachable. Fail open so ACC being down never bricks the agent;
                  # ACC's value is visibility, and a dead dashboard must not block work.
                  exit 0
                fi

                printf '%%s' "$RESPONSE"
                exit 0
                """.formatted(port);
    }
}
