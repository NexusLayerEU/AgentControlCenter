package eu.nexuslayer.acc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves ACC's on-disk home directory before the Spring context exists. */
public final class AccPaths {

    private AccPaths() {
    }

    public static Path home() {
        String override = System.getenv("ACC_HOME");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".acc");
    }

    public static Path ensureHome() {
        Path home = home();
        try {
            Files.createDirectories(home);
            Files.createDirectories(home.resolve("logs"));
            return home;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create ACC home at " + home, e);
        }
    }
}
