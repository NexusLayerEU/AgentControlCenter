package eu.nexuslayer.acc.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The auto-approve rule is the product decision this whole daemon turns on:
 * a session launched to run unattended must never block on a human.
 */
class StartSessionRequestTest {

    private StartSessionRequest withMode(String mode) {
        return new StartSessionRequest("n", "do a thing", "/tmp", null, mode, null);
    }

    @Test
    @DisplayName("default mode gates tool calls")
    void defaultModeGates() {
        assertFalse(withMode("default").resolvedAutoApprove());
        assertFalse(withMode(null).resolvedAutoApprove());
        assertFalse(withMode("").resolvedAutoApprove());
    }

    @Test
    @DisplayName("acceptEdits and bypassPermissions run unattended")
    void unattendedModesAutoApprove() {
        assertTrue(withMode("acceptEdits").resolvedAutoApprove());
        assertTrue(withMode("bypassPermissions").resolvedAutoApprove());
    }

    @Test
    @DisplayName("plan mode still gates because the agent can still be asked to act")
    void planModeGates() {
        assertFalse(withMode("plan").resolvedAutoApprove());
    }

    @Test
    @DisplayName("an explicit autoApprove flag overrides the mode default")
    void explicitFlagWins() {
        assertTrue(new StartSessionRequest("n", "p", "/tmp", null, "default", Boolean.TRUE)
                .resolvedAutoApprove());
        assertFalse(new StartSessionRequest("n", "p", "/tmp", null, "bypassPermissions", Boolean.FALSE)
                .resolvedAutoApprove());
    }

    @Test
    @DisplayName("an unrecognised mode falls back to the safe default")
    void unknownModeIsSafe() {
        StartSessionRequest request = withMode("yolo");
        assertEquals("default", request.resolvedMode());
        assertFalse(request.resolvedAutoApprove());
    }

    @Test
    @DisplayName("a missing name is derived from the prompt")
    void nameDerivedFromPrompt() {
        StartSessionRequest request = new StartSessionRequest(null,
                "refactor   the\nauth module", "/tmp", null, null, null);
        assertEquals("refactor the auth module", request.resolvedName());
    }

    @Test
    @DisplayName("a long derived name is truncated")
    void longNameTruncated() {
        String prompt = "x".repeat(200);
        assertEquals(60, new StartSessionRequest(null, prompt, "/tmp", null, null, null)
                .resolvedName().length());
    }
}
