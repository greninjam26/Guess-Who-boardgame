package com.guesswho.ui;

import com.guesswho.client.ApplicationDirectory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the one game a player has in progress.
 *
 * <p>One slot. Somebody part-way through a game wants that game back, not a
 * list of games to choose between.</p>
 *
 * <p>Every failure here is survivable, and all of them are treated the same
 * way: there is no game to resume. A save is a convenience, and one that
 * stopped the application launching because a file would not parse would be a
 * far worse bug than the one it was trying to help with.</p>
 */
class SavedGameStore {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            //Records gain accessors that are not components, and a save written
            //by a later version may carry fields this one has never heard of.
            //Neither is a reason to throw away somebody's game.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Path file;

    /** Keeps the save beside the upload queue, in the user's own directory. */
    SavedGameStore() {
        this(ApplicationDirectory.forThisMachine().resolve("saved-game.json"));
    }

    SavedGameStore(Path file) {
        this.file = file;
    }

    //Jackson 3 throws unchecked, so RuntimeException is what covers a file
    //that will not parse; there is no separate checked case to name.
    /**
     * Writes the game in progress, replacing any previous one.
     *
     * @param game the game to keep
     * @return whether it was written
     */
    boolean save(SavedGame game) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, JSON_MAPPER.writeValueAsString(game),
                    StandardCharsets.UTF_8);
            return true;
        }
        catch (IOException | RuntimeException unwritable) {
            //Losing a save is a disappointment. Interrupting a game in order to
            //report it would be worse, and there is nothing a player could do.
            return false;
        }
    }

    /**
     * Reads the game in progress, if there is one worth offering back.
     *
     * @return the saved game, or empty when there is none, it cannot be read,
     *         or it was written by a different version
     */
    Optional<SavedGame> read() {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            SavedGame saved =
                    JSON_MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8),
                            SavedGame.class);
            return saved != null && saved.isReadable() ? Optional.of(saved) : Optional.empty();
        }
        catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Forgets the game in progress, once it is finished or abandoned.
     *
     * <p>A finished game offered back on the next launch would look like the
     * application had failed to notice it ended.</p>
     */
    void clear() {
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException leaveIt) {
            //It will be overwritten by the next save, or fail to parse and be
            //discarded on the next read.
        }
    }

    /** Whether a game is waiting to be resumed. */
    boolean hasSavedGame() {
        return read().isPresent();
    }
}
