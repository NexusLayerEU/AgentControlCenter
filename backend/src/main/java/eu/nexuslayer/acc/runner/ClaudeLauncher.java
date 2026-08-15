package eu.nexuslayer.acc.runner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.nexuslayer.acc.Platform;

/**
 * Resolves the {@code claude} launcher to something {@link ProcessBuilder} can
 * actually execute.
 *
 * <p>On Windows an npm-installed CLI is a {@code .cmd} shim; exec'ing the bare
 * name fails with "CreateProcess error=2". The resolved name is cached because
 * it cannot change while the daemon is running.
 */
public final class ClaudeLauncher {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLauncher.class);

    private static volatile String cached;
    private static volatile String cachedFor;

    private ClaudeLauncher() {
    }

    public static String resolve(String configured) {
        if (configured == null || configured.isBlank()) {
            configured = "claude";
        }
        if (configured.equals(cachedFor) && cached != null) {
            return cached;
        }

        String resolved = probe(configured);
        cachedFor = configured;
        cached = resolved;
        return resolved;
    }

    private static String probe(String configured) {
        // An explicit path is taken at face value.
        if (configured.contains(File.separator) || configured.contains("/")) {
            return configured;
        }
        for (String candidate : Platform.executableCandidates(configured)) {
            if (onPath(candidate)) {
                return candidate;
            }
        }
        log.warn("Could not find '{}' on PATH; using it as given", configured);
        return configured;
    }

    private static boolean onPath(String candidate) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String entry : path.split(File.pathSeparator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path file = Path.of(entry, candidate);
                if (Files.isRegularFile(file) && (Platform.isWindows() || Files.isExecutable(file))) {
                    return true;
                }
            } catch (RuntimeException e) {
                // A malformed PATH entry must not stop the search.
            }
        }
        return false;
    }

    /** Runs {@code <claude> --version} and returns the output, or null if unavailable. */
    public static String version(String configured) {
        try {
            Process process = new ProcessBuilder(resolve(configured), "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return process.exitValue() == 0 ? output : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
