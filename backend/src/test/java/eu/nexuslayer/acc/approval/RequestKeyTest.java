package eu.nexuslayer.acc.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import eu.nexuslayer.acc.util.Json;

class RequestKeyTest {

    @Test
    @DisplayName("the same call from the same session collapses to one key")
    void identicalCallsShareKey() {
        assertEquals(
                RequestKey.of("s1", "Bash", Json.read("{\"command\":\"echo hi\"}")),
                RequestKey.of("s1", "Bash", Json.read("{\"command\":\"echo hi\"}")));
    }

    @Test
    @DisplayName("a different command is a different decision")
    void differentInputDiffersKey() {
        assertNotEquals(
                RequestKey.of("s1", "Bash", Json.read("{\"command\":\"echo hi\"}")),
                RequestKey.of("s1", "Bash", Json.read("{\"command\":\"rm -rf /\"}")));
    }

    @Test
    @DisplayName("the same command in another session is decided separately")
    void differentSessionDiffersKey() {
        assertNotEquals(
                RequestKey.of("s1", "Bash", Json.read("{\"command\":\"echo hi\"}")),
                RequestKey.of("s2", "Bash", Json.read("{\"command\":\"echo hi\"}")));
    }

    @Test
    @DisplayName("the same input to a different tool is decided separately")
    void differentToolDiffersKey() {
        assertNotEquals(
                RequestKey.of("s1", "Bash", Json.read("{\"x\":1}")),
                RequestKey.of("s1", "Write", Json.read("{\"x\":1}")));
    }

    @Test
    @DisplayName("null session, tool and input are handled without throwing")
    void handlesNulls() {
        assertEquals(RequestKey.of(null, null, null), RequestKey.of(null, null, null));
        assertNotEquals(RequestKey.of(null, null, null),
                RequestKey.of("s1", "Bash", Json.read("{}")));
    }
}
