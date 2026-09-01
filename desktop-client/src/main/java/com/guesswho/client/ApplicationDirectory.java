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
        return firstUsable(preferred(), temporary());
    }

    /**
     * The first of these the game can actually write to.
     *
     * <p>Creating a directory succeeds when it already exists, whatever its
     * permissions, so existing is not the same as usable. A directory that
     * cannot be written to is worse than no directory at all: everything looks
     * fine until the first save fails.</p>
     *
     * @param candidates the places to try, best first
     * @return the first writable one, or the working directory if none are
     */
    static Path firstUsable(Path... candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && isUsable(candidate)) {
                return candidate;
            }
        }
        //Where these files went before any of this existed. It may not be
        //writable either, and what happens then is not uniform, so it is worth
        //being exact rather than reassuring: SavedGameStore and TokenStore
        //swallow a failed write, losing a resumable game or a remembered login
        //and nothing else. FilePendingGameResultStore does not — it throws
        //UncheckedIOException, deliberately, because a queue that silently
        //dropped results is the bug the write-only CSV had. That throw happens
        //inside GameResultSubmissionService's future, so it reaches the player
        //as "the game result could not be stored" rather than as a crash.
        //
        //Which is the point: reaching here is survivable, not harmless. Every
        //candidate above it has already been created and written to, so this
        //returns only when the machine has no writable directory of its own to
        //offer.
        return Path.of("");
    }

    private static boolean isUsable(Path directory) {
        try {
            Files.createDirectories(directory);
            return Files.isDirectory(directory) && Files.isWritable(directory);
        }
        catch (IOException | RuntimeException unusable) {
            //RuntimeException as well: a restrictive security manager throws
            //rather than returning false, and a game that will not start
            //because of one is worse than a game that saves nothing.
            return false;
        }
    }

    private static Path preferred() {
        try {
            return resolve(
                    System.getProperty("os.name", ""),
                    System.getenv("APPDATA"),
                    System.getenv("XDG_DATA_HOME"),
                    System.getProperty("user.home", ""));
        }
        catch (RuntimeException unavailable) {
            //Reading a property or a variable can be refused outright.
            return null;
        }
    }

    /** Lost on a reboot, but writable, which the alternative may not be. */
    private static Path temporary() {
        try {
            return Path.of(System.getProperty("java.io.tmpdir", "."), UNIX_NAME);
        }
        catch (RuntimeException unavailable) {
            return null;
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
