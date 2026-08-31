package com.guesswho.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Where the game keeps the files it writes for itself.
 *
 * <p>A relative path is resolved against the working directory, which is the
 * directory a developer happens to run Maven from. An installed application has
 * no such directory: launched from the Finder or the Start menu it inherits
 * whatever the launcher had, often the root of the disk, where it cannot
 * write. Anything the game saves has to go somewhere chosen deliberately.</p>
 *
 * <p>Each system has its own answer, and using the local one means the files
 * land where a user would look for them and where an uninstaller would think to
 * remove them.</p>
 */
public final class ApplicationDirectory {
    /** Spelled as the platforms spell application names, except on Linux. */
    private static final String NAME = "Guess Who";
    private static final String UNIX_NAME = "guess-who";

    private ApplicationDirectory() {
    }

    /**
     * Returns the directory this machine keeps application data in, creating it.
     *
     * @return an existing directory the game can write to
     */
    public static Path forThisMachine() {
        Path directory = resolve(
                System.getProperty("os.name", ""),
                System.getenv("APPDATA"),
                System.getenv("XDG_DATA_HOME"),
                System.getProperty("user.home", ""));
        try {
            Files.createDirectories(directory);
            return directory;
        }
        catch (IOException unwritable) {
            //A read-only or unusual home should not stop the game starting. The
            //working directory is where these files went before, so falling
            //back to it leaves things no worse than they were.
            return Path.of("");
        }
    }

    /**
     * Works out the directory for a system, without touching the disk.
     *
     * @param osName      the {@code os.name} property
     * @param appData     the Windows {@code APPDATA} variable, or null
     * @param dataHome    the {@code XDG_DATA_HOME} variable, or null
     * @param home        the {@code user.home} property
     * @return the directory that system keeps application data in
     */
    static Path resolve(String osName, String appData, String dataHome, String home) {
        String system = osName.toLowerCase(Locale.ROOT);
        if (system.startsWith("mac") || system.startsWith("darwin")) {
            return Path.of(home, "Library", "Application Support", NAME);
        }
        if (system.startsWith("windows")) {
            //APPDATA is what Windows itself uses, and it follows a roaming
            //profile between machines. Its usual value is the fallback.
            return set(appData)
                    ? Path.of(appData, NAME)
                    : Path.of(home, "AppData", "Roaming", NAME);
        }
        return set(dataHome)
                ? Path.of(dataHome, UNIX_NAME)
                : Path.of(home, ".local", "share", UNIX_NAME);
    }

    private static boolean set(String variable) {
        return variable != null && !variable.isBlank();
    }
}
