package eu.nexuslayer.acc;

import java.util.List;

/**
 * Host-OS differences ACC actually has to care about.
 *
 * <p>The daemon jar itself is portable — both pty4j and sqlite-jdbc ship natives
 * for every supported platform — but three things are not: the hook bridge
 * script Claude Code executes, the shell a terminal pane opens, and how the
 * {@code claude} launcher is resolved.
 */
public final class Platform {

    private static final String OS = System.getProperty("os.name", "").toLowerCase();

    private Platform() {
    }

    public static boolean isWindows() {
        return OS.contains("win");
    }

    public static boolean isMac() {
        return OS.contains("mac") || OS.contains("darwin");
    }

    /** File name of the generated hook bridge for this platform. */
    public static String hookScriptName() {
        return isWindows() ? "acc-hook.ps1" : "acc-hook.sh";
    }

    /**
     * The command Claude Code should run for a hook. On Windows a {@code .ps1}
     * is not directly executable, so PowerShell has to be invoked explicitly.
     */
    public static String hookCommand(String scriptPath, String endpoint) {
        if (isWindows()) {
            return "powershell -NoProfile -ExecutionPolicy Bypass -File \"" + scriptPath + "\" " + endpoint;
        }
        return "\"" + scriptPath + "\" " + endpoint;
    }

    /** Interactive shell for a terminal pane, honouring $SHELL where it exists. */
    public static List<String> loginShell() {
        if (isWindows()) {
            return List.of("powershell.exe", "-NoLogo");
        }
        String shell = System.getenv("SHELL");
        return List.of(shell == null || shell.isBlank() ? "/bin/bash" : shell, "-l");
    }

    /** Runs a one-off command in the platform shell. */
    public static List<String> shellCommand(String command) {
        if (isWindows()) {
            return List.of("powershell.exe", "-NoLogo", "-NoProfile", "-Command", command);
        }
        String shell = System.getenv("SHELL");
        return List.of(shell == null || shell.isBlank() ? "/bin/bash" : shell, "-l", "-c", command);
    }

    /**
     * Candidate launcher names for a CLI. On Windows an npm-installed tool is a
     * {@code .cmd} shim, which {@link ProcessBuilder} cannot exec by bare name.
     */
    public static List<String> executableCandidates(String binary) {
        if (!isWindows() || binary.matches(".*\\.(cmd|bat|exe)$")) {
            return List.of(binary);
        }
        return List.of(binary + ".cmd", binary + ".exe", binary + ".bat", binary);
    }
}
