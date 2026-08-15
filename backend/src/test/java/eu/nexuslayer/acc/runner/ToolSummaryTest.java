package eu.nexuslayer.acc.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import eu.nexuslayer.acc.util.Json;

class ToolSummaryTest {

    private JsonNode input(String json) {
        return Json.read(json);
    }

    @Test
    @DisplayName("bash calls are summarised by their command")
    void describesBash() {
        assertEquals("ls -la /tmp", ToolSummary.describe("Bash", input("{\"command\":\"ls -la /tmp\"}")));
    }

    @Test
    @DisplayName("multi-line commands collapse to one line")
    void collapsesWhitespace() {
        assertEquals("a && b", ToolSummary.describe("Bash", input("{\"command\":\"a &&\\n   b\"}")));
    }

    @Test
    @DisplayName("paths inside the session directory are shown relative to it")
    void relativisesToWorkingDir() {
        assertEquals("src/tokens.js",
                ToolSummary.describe("Read", input("{\"file_path\":\"/work/proj/src/tokens.js\"}"), "/work/proj"));
    }

    @Test
    @DisplayName("a shell command has the session directory stripped out of it")
    void relativisesCommands() {
        assertEquals("ls -la src",
                ToolSummary.describe("Bash", input("{\"command\":\"ls -la /work/proj/src\"}"), "/work/proj"));
    }

    @Test
    @DisplayName("a reference to the session directory itself becomes a dot")
    void collapsesWorkingDirToDot() {
        assertEquals("ls -la .",
                ToolSummary.describe("Bash", input("{\"command\":\"ls -la /work/proj\"}"), "/work/proj"));
        assertEquals(".",
                ToolSummary.describe("Read", input("{\"file_path\":\"/work/proj\"}"), "/work/proj"));
    }

    @Test
    @DisplayName("a trailing slash on the working directory is tolerated")
    void toleratesTrailingSlash() {
        assertEquals("src/a.js",
                ToolSummary.describe("Read", input("{\"file_path\":\"/work/proj/src/a.js\"}"), "/work/proj/"));
    }

    @Test
    @DisplayName("paths outside the session directory keep their absolute form")
    void leavesOutsidePathsAlone() {
        assertEquals("/etc/hosts",
                ToolSummary.describe("Read", input("{\"file_path\":\"/etc/hosts\"}"), "/work/proj"));
    }

    @Test
    @DisplayName("home-relative paths are shortened")
    void shortensHomePaths() {
        String home = System.getProperty("user.home");
        String described = ToolSummary.describe("Read", input("{\"file_path\":\"" + home + "/x.txt\"}"));
        assertEquals("~/x.txt", described);
    }

    @Test
    @DisplayName("destructive shell commands are flagged")
    void flagsDestructiveCommands() {
        assertEquals("destructive", ToolSummary.risk("Bash", input("{\"command\":\"rm -rf /tmp/x\"}")));
        assertEquals("destructive", ToolSummary.risk("Bash", input("{\"command\":\"DROP TABLE users\"}")));
        assertEquals("destructive", ToolSummary.risk("Bash", input("{\"command\":\"git push --force\"}")));
    }

    @Test
    @DisplayName("ordinary shell commands are elevated, not destructive")
    void ordinaryCommandsElevated() {
        assertEquals("elevated", ToolSummary.risk("Bash", input("{\"command\":\"npm test\"}")));
    }

    @Test
    @DisplayName("read-only tools are safe")
    void readOnlyToolsSafe() {
        assertEquals("safe", ToolSummary.risk("Read", input("{}")));
        assertEquals("safe", ToolSummary.risk("Grep", input("{}")));
    }

    @Test
    @DisplayName("writes are elevated")
    void writesElevated() {
        assertEquals("elevated", ToolSummary.risk("Write", input("{}")));
        assertEquals("elevated", ToolSummary.risk("Edit", input("{}")));
    }

    @Test
    @DisplayName("a bash call with no command is treated as elevated rather than safe")
    void missingCommandIsNotSafe() {
        assertEquals("elevated", ToolSummary.risk("Bash", input("{}")));
    }

    @Test
    @DisplayName("very long summaries are truncated with an ellipsis")
    void truncatesLongSummaries() {
        String described = ToolSummary.describe("Bash",
                input("{\"command\":\"" + "x".repeat(400) + "\"}"));
        assertEquals(120, described.length());
        assertTrue(described.endsWith("…"));
    }
}
