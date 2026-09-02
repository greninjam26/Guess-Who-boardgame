package com.guesswho.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

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
     * The directory this machine keeps application data in, creating it.
     *
     * <p>The working directory is the last candidate and is checked like every
     * other, so a directory that comes back from here has been created and
     * confirmed writable — with one stated exception. When nothing at all is
     * usable there is still a path to return, because each caller resolves a
     * filename against it, and refusing to start the game over an unwritable
     * disk would be a worse answer than a game that cannot save. In that one
     * case the working directory comes back unusable, and writes through it
     * fail: {@code SavedGameStore} and {@code TokenStore} swallow that, losing
     * a resumable game or a remembered login; {@code FilePendingGameResultStore}
     * throws, deliberately, and the player is told the result could not be
     * stored.</p>
     *
     * <p>{@link #writableForThisMachine()} is the form that says so in its
     * return type rather than in a paragraph. Prefer it anywhere the difference
     * can actually be acted on.</p>
     *
     * @return a writable directory, or the working directory when none exists
     */
    public static Path forThisMachine() {
        return writableForThisMachine().orElseGet(() -> Path.of(""));
    }

    /**
     * The directory this machine can keep application data in, if there is one.
     *
     * @return the first usable directory, or empty when this machine has no
     *         writable storage to offer
     */
    public static Optional<Path> writableForThisMachine() {
        //The working directory last rather than as an unchecked fallback: it is
        //a real candidate, it is sometimes the right answer, and the promise
        //this class makes is only worth anything if everything it returns has
        //been tried.
        return firstUsable(preferred(), temporary(), Path.of(""));
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
     * @return the first writable one, or empty when none of them is
     */
    static Optional<Path> firstUsable(Path... candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && isUsable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
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
