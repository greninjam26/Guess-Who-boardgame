package com.guesswho.client;

import com.guesswho.room.RoomCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Remembers which online room this machine was last in.
 *
 * <p>Its own file rather than a field on the saved local game, because the two
 * are not the same kind of thing. A saved local game is the whole game — this
 * client is the only place it exists. An online room is a six-character code:
 * the game itself lives on the server, which is also what makes rejoining
 * possible at all. Putting the code on {@code SavedGame} would add a field that
 * is null for every local game and mean one slot for two unrelated things a
 * player can genuinely have at once — a half-finished game against the computer
 * and an open room with a friend.</p>
 *
 * <p>Nothing here throws. A code that cannot be read means the offer to rejoin
 * is not made, which is a worse launch than it could have been and a much
 * better one than a game that refuses to start.</p>
 */
public class ActiveRoomStore {
    private final Path file;

    /** Keeps the code beside the saved game and the token. */
    public ActiveRoomStore() {
        this(ApplicationDirectory.forThisMachine().resolve("active-room"));
    }

    /**
     * Keeps the code in a given file.
     *
     * @param file where to keep it
     */
    public ActiveRoomStore(Path file) {
        this.file = file;
    }

    /**
     * Remembers the room a game is being played in.
     *
     * @param code the room's code
     * @return whether it was stored; false means no offer to rejoin next launch
     */
    public boolean save(String code) {
        String tidied = RoomCode.normalise(code);
        if (tidied == null) {
            return false;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, tidied, StandardCharsets.UTF_8);
            return true;
        }
        catch (IOException | RuntimeException unwritable) {
            return false;
        }
    }

    /**
     * The room this machine was last in, if there is one worth offering.
     *
     * @return the stored code, or empty when there is none or it is unusable
     */
    public Optional<String> read() {
        try {
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            //Normalised on the way out as well as in. A file somebody edited, or
            //one left by an older build, must not become a request for a room
            //whose code could never exist.
            return Optional.ofNullable(
                    RoomCode.normalise(Files.readString(file, StandardCharsets.UTF_8)));
        }
        catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Forgets the room, once the game in it is over or left.
     *
     * <p>Called on every way out of a game, including the ones that are not
     * wins: a finished game, a room that expired, and a player who walked back
     * to the menu. A code left behind offers a game that cannot be rejoined,
     * and the player has to decline it on every launch to make it stop.</p>
     */
    public void clear() {
        try {
            Files.deleteIfExists(file);
        }
        catch (IOException | RuntimeException leaveIt) {
            //Nothing to do about it, and nothing that follows depends on it:
            //an offer that cannot be honoured is refused by the server anyway.
        }
    }
}
