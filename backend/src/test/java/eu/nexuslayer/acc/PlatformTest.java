package eu.nexuslayer.acc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class PlatformTest {

    @Test
    @DisplayName("exactly one OS family is reported")
    void identifiesOneFamily() {
        assertFalse(Platform.isWindows() && Platform.isMac());
    }

    @Test
    @DisplayName("the hook command quotes the script path, so spaces in it survive")
    void quotesScriptPath() {
        String command = Platform.hookCommand("/Users/a b/.acc/acc-hook.sh", "pre-tool-use");
        assertTrue(command.contains("\"/Users/a b/.acc/acc-hook.sh\""),
                "an unquoted path with a space would split into two arguments");
        assertTrue(command.endsWith("pre-tool-use"));
    }

    @Test
    @DisplayName("a bare CLI name gets no candidates added on POSIX")
    @EnabledOnOs({ OS.MAC, OS.LINUX })
    void posixLeavesNamesAlone() {
        assertEquals(List.of("claude"), Platform.executableCandidates("claude"));
        assertEquals("acc-hook.sh", Platform.hookScriptName());
        assertTrue(Platform.loginShell().get(1).equals("-l"));
    }

    @Test
    @DisplayName("on Windows the .cmd shim is tried before the bare name")
    @EnabledOnOs(OS.WINDOWS)
    void windowsPrefersCmdShim() {
        List<String> candidates = Platform.executableCandidates("claude");
        assertEquals("claude.cmd", candidates.get(0),
                "an npm-installed CLI on Windows is a .cmd shim; the bare name cannot be exec'd");
        assertTrue(candidates.contains("claude"));
        assertEquals("acc-hook.ps1", Platform.hookScriptName());
        assertTrue(Platform.hookCommand("C:\\x\\acc-hook.ps1", "stop").startsWith("powershell"));
        assertEquals("powershell.exe", Platform.loginShell().get(0));
    }

    @Test
    @DisplayName("an explicit extension is never double-suffixed")
    void doesNotDoubleSuffix() {
        assertEquals(List.of("claude.cmd"), Platform.executableCandidates("claude.cmd"));
        assertEquals(List.of("claude.exe"), Platform.executableCandidates("claude.exe"));
    }
}
