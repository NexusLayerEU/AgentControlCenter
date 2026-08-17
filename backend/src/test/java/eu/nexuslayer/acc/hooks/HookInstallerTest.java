package eu.nexuslayer.acc.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.util.Json;

/**
 * Installing hooks edits a file the user owns and may have customised, so the
 * merge behaviour is tested rather than assumed.
 */
class HookInstallerTest {

    private final HookInstaller installer = new HookInstaller();

    private JsonNode settingsAt(Path projectDir) throws Exception {
        return Json.read(Files.readString(projectDir.resolve(".claude/settings.json"),
                StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("install writes an executable bridge script and registers every hook")
    void installsHooks(@TempDir Path projectDir) throws Exception {
        HookInstaller.InstallResult result = installer.install(4000, true, projectDir.toString());

        Path script = Path.of(result.scriptPath());
        assertTrue(Files.exists(script));
        assertTrue(Files.isExecutable(script));
        assertTrue(Files.readString(script).contains("127.0.0.1:4000/hooks/"));

        JsonNode hooks = settingsAt(projectDir).path("hooks");
        assertTrue(hooks.has("PreToolUse"));
        assertTrue(hooks.has("PostToolUse"));
        assertTrue(hooks.has("Stop"));
        assertEquals(6, result.installed().size());
    }

    @Test
    @DisplayName("SessionEnd is registered — it is how an adopted session knows it closed")
    void registersSessionEnd(@TempDir Path projectDir) throws Exception {
        installer.install(4000, true, projectDir.toString());
        JsonNode hooks = settingsAt(projectDir).path("hooks");

        assertTrue(hooks.has("SessionEnd"),
                "without it, a session can only be aged out on a guess");
        assertTrue(hooks.path("SessionEnd").get(0).path("hooks").get(0)
                .path("command").asText().contains("session-end"));
    }

    @Test
    @DisplayName("only PreToolUse gets the long timeout it needs to block")
    void blockingHookGetsLongTimeout(@TempDir Path projectDir) throws Exception {
        installer.install(4000, true, projectDir.toString());
        JsonNode hooks = settingsAt(projectDir).path("hooks");

        assertEquals(60, hooks.path("PreToolUse").get(0).path("hooks").get(0).path("timeout").asInt());
        assertEquals(10, hooks.path("PostToolUse").get(0).path("hooks").get(0).path("timeout").asInt());
    }

    @Test
    @DisplayName("existing user settings and unrelated hooks survive installation")
    void preservesUserSettings(@TempDir Path projectDir) throws Exception {
        Path settings = projectDir.resolve(".claude/settings.json");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, """
                {
                  "model": "claude-opus-5",
                  "hooks": {
                    "PreToolUse": [
                      {"matcher": "Bash", "hooks": [{"type": "command", "command": "my-own-linter"}]}
                    ]
                  }
                }
                """);

        installer.install(4000, true, projectDir.toString());
        JsonNode root = settingsAt(projectDir);

        assertEquals("claude-opus-5", root.path("model").asText());

        JsonNode preToolUse = root.path("hooks").path("PreToolUse");
        assertEquals(2, preToolUse.size());
        assertEquals("my-own-linter",
                preToolUse.get(0).path("hooks").get(0).path("command").asText());
    }

    @Test
    @DisplayName("reinstalling replaces ACC's entry instead of duplicating it")
    void reinstallIsIdempotent(@TempDir Path projectDir) throws Exception {
        installer.install(4000, true, projectDir.toString());
        installer.install(4000, true, projectDir.toString());
        installer.install(4000, true, projectDir.toString());

        assertEquals(1, settingsAt(projectDir).path("hooks").path("PreToolUse").size());
    }

    @Test
    @DisplayName("uninstall removes ACC's entries and leaves the user's own alone")
    void uninstallLeavesUserHooks(@TempDir Path projectDir) throws Exception {
        Path settings = projectDir.resolve(".claude/settings.json");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, """
                {"hooks": {"PreToolUse": [
                  {"matcher": "Bash", "hooks": [{"type": "command", "command": "my-own-linter"}]}]}}
                """);

        installer.install(4000, true, projectDir.toString());
        installer.uninstall(true, projectDir.toString());

        JsonNode preToolUse = settingsAt(projectDir).path("hooks").path("PreToolUse");
        assertEquals(1, preToolUse.size());
        assertEquals("my-own-linter", preToolUse.get(0).path("hooks").get(0).path("command").asText());
    }

    @Test
    @DisplayName("isInstalled reflects the real state of the settings file")
    void reportsInstallState(@TempDir Path projectDir) throws Exception {
        assertFalse(installer.isInstalled(true, projectDir.toString()));
        installer.install(4000, true, projectDir.toString());
        assertTrue(installer.isInstalled(true, projectDir.toString()));
        installer.uninstall(true, projectDir.toString());
        assertFalse(installer.isInstalled(true, projectDir.toString()));
    }

    @Test
    @DisplayName("the generated bridge fails open so a dead daemon never blocks the agent")
    void scriptFailsOpen(@TempDir Path projectDir) throws Exception {
        HookInstaller.InstallResult result = installer.install(4000, true, projectDir.toString());
        String script = Files.readString(Path.of(result.scriptPath()));

        assertTrue(script.contains("exit 0"), "must exit successfully when the daemon is unreachable");
        assertTrue(script.contains("127.0.0.1:4000/hooks/") || script.contains("127.0.0.1:4000/hooks/'"),
                "must target the local daemon");
        // The wait budget must stay under Claude Code's own 60s hook timeout.
        assertTrue(script.contains("58"), "bridge timeout must leave headroom under the hook timeout");
    }

    @Test
    @DisplayName("the bridge written matches the host platform")
    void writesPlatformAppropriateBridge(@TempDir Path projectDir) throws Exception {
        HookInstaller.InstallResult result = installer.install(4000, true, projectDir.toString());
        String script = Files.readString(Path.of(result.scriptPath()));

        if (eu.nexuslayer.acc.Platform.isWindows()) {
            assertTrue(result.scriptPath().endsWith("acc-hook.ps1"));
            assertTrue(script.contains("Invoke-RestMethod"));
            assertTrue(settingsAt(projectDir).path("hooks").path("PreToolUse").get(0)
                    .path("hooks").get(0).path("command").asText().startsWith("powershell"),
                    "Claude Code cannot exec a .ps1 directly");
        } else {
            assertTrue(result.scriptPath().endsWith("acc-hook.sh"));
            assertTrue(script.startsWith("#!/usr/bin/env bash"));
            assertTrue(Files.isExecutable(Path.of(result.scriptPath())));
        }
    }
}
