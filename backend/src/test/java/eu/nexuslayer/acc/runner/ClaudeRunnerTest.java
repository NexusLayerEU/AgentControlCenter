package eu.nexuslayer.acc.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import eu.nexuslayer.acc.config.AccProperties;

/**
 * The argument mapping is the contract between ACC and the Claude Code CLI —
 * getting a permission flag wrong here silently changes whether an agent is
 * gated, so it is asserted directly rather than only exercised end to end.
 */
class ClaudeRunnerTest {

    private ClaudeRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ClaudeRunner(new AccProperties("/tmp/acc", "claude", 50, "observe", true, 8000), null);
    }

    private List<String> command(String mode, String model) {
        return runner.buildCommand(new StartSessionRequest("n", "do it", "/tmp", model, mode, null));
    }

    @Test
    @DisplayName("every run asks for the structured stream the parser depends on")
    void alwaysRequestsStreamJson() {
        List<String> cmd = command("default", null);
        assertEquals("claude", cmd.get(0));
        assertEquals("-p", cmd.get(1));
        assertEquals("do it", cmd.get(2));
        assertTrue(cmd.contains("--output-format"));
        assertEquals("stream-json", cmd.get(cmd.indexOf("--output-format") + 1));
        assertTrue(cmd.contains("--verbose"), "stream-json requires --verbose to emit tool blocks");
    }

    @Test
    @DisplayName("default mode passes no permission flag, leaving the gate to the hook")
    void defaultModePassesNoPermissionFlag() {
        List<String> cmd = command("default", null);
        assertFalse(cmd.contains("--permission-mode"));
        assertFalse(cmd.contains("--dangerously-skip-permissions"));
    }

    @Test
    @DisplayName("acceptEdits and plan are forwarded as --permission-mode")
    void forwardsPermissionMode() {
        List<String> accept = command("acceptEdits", null);
        assertEquals("acceptEdits", accept.get(accept.indexOf("--permission-mode") + 1));

        List<String> plan = command("plan", null);
        assertEquals("plan", plan.get(plan.indexOf("--permission-mode") + 1));
    }

    @Test
    @DisplayName("bypassPermissions uses the skip flag, not --permission-mode")
    void bypassUsesSkipFlag() {
        List<String> cmd = command("bypassPermissions", null);
        assertTrue(cmd.contains("--dangerously-skip-permissions"));
        assertFalse(cmd.contains("--permission-mode"));
    }

    @Test
    @DisplayName("an unrecognised mode degrades to the gated default")
    void unknownModeIsGated() {
        List<String> cmd = command("yolo", null);
        assertFalse(cmd.contains("--dangerously-skip-permissions"));
        assertFalse(cmd.contains("--permission-mode"));
    }

    @Test
    @DisplayName("a model is forwarded only when one was requested")
    void forwardsModelWhenPresent() {
        List<String> withModel = command("default", "claude-opus-5");
        assertEquals("claude-opus-5", withModel.get(withModel.indexOf("--model") + 1));

        assertFalse(command("default", null).contains("--model"));
        assertFalse(command("default", "  ").contains("--model"));
    }

    @Test
    @DisplayName("the configured binary is honoured so a non-PATH install still works")
    void honoursConfiguredBinary() {
        ClaudeRunner custom = new ClaudeRunner(
                new AccProperties("/tmp/acc", "/opt/bin/claude", 50, "observe", true, 8000), null);
        assertEquals("/opt/bin/claude",
                custom.buildCommand(new StartSessionRequest("n", "p", "/tmp", null, "default", null)).get(0));
    }

    @Test
    @DisplayName("cancelling a session that was never started is a no-op, not an error")
    void cancelUnknownSessionIsSafe() {
        assertFalse(runner.cancel("no-such-session"));
        assertFalse(runner.isRunning("no-such-session"));
    }
}
