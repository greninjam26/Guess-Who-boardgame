package com.guesswho.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps the token that says somebody is logged in, so the game does not ask
 * again on every launch.
 *
 * <p>A file rather than {@code java.util.prefs}, which the settings use: on
 * macOS preferences are world-readable plists, and a token is a password by
 * another name. This file is written owner-only where the system allows it.</p>
 *
 * <p>A token that cannot be read is the same as not being logged in. Nothing
 * here throws, because a login prompt is a survivable outcome and a game that
 * refuses to start is not.</p>
 */
public class TokenStore {
    private final Path file;

    /** Keeps the token beside the saved game, in the user's own directory. */
    public TokenStore() {
        this(ApplicationDirectory.forThisMachine().resolve("session-token"));
    }

    /**
     * Keeps the token in a given file.
     *
     * @param file where to keep it
     */
    public TokenStore(Path file) {
        this.file = file;
    }

    /**
     * Remembers a token.
     *
     * @param token the token the server issued
     * @return whether it was stored; false means the player will log in again
     */
    public boolean save(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, token, StandardCharsets.UTF_8);
            restrictToOwner();
            return true;
        }
        catch (IOException | RuntimeException unwritable) {
            return false;
        }
    }

    /**
     * The token from a previous session, if there is a usable one.
     *
     * @return the token, or empty when none is stored or it cannot be read
     */
    public Optional<String> read() {
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            String token = Files.readString(file, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        }
        catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Forgets the token, on logout.
     *
     * <p>The server is told separately. Both matter: forgetting it here without
     * telling the server leaves a token that still works, and telling the
     * server without forgetting it here leaves the game trying a dead one.</p>
     */
    public void clear() {
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException leaveIt) {
            //An unreadable or undeletable token behaves as no token at all.
        }
    }

    /** Owner-only where the filesystem supports it; silently skipped on Windows. */
    private void restrictToOwner() {
        try {
            Files.setPosixFilePermissions(file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        catch (IOException | UnsupportedOperationException notPosix) {
            //Windows has no POSIX permissions. The file still sits in the
            //user's own application directory, which is the protection there.
        }
    }
}
