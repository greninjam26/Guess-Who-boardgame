package com.guesswho.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActiveRoomStoreTest {
    @TempDir
    private Path directory;

    @Test
    void remembersTheRoomAcrossRestarts() {
        ActiveRoomStore store = new ActiveRoomStore(directory.resolve("active-room"));

        assertTrue(store.save("BCDFGH"));

        //A second store reading the same file is what a relaunch actually is.
        assertEquals(Optional.of("BCDFGH"),
                new ActiveRoomStore(directory.resolve("active-room")).read());
    }

    @Test
    void offersNothingWhenNoGameWasBeingPlayed() {
        ActiveRoomStore store = new ActiveRoomStore(directory.resolve("nothing-here"));

        assertEquals(Optional.empty(), store.read());
    }

    @Test
    void forgetsTheRoomOnceTheGameIsOver() {
        ActiveRoomStore store = new ActiveRoomStore(directory.resolve("active-room"));
        store.save("BCDFGH");

        store.clear();

        assertEquals(Optional.empty(), store.read(),
                "A code left behind offers a game that cannot be rejoined");
    }

    @Test
    void tidiesTheCodeOnTheWayIn() {
        //The same normalising the server does, so what is stored is what a room
        //is actually called rather than what somebody typed.
        ActiveRoomStore store = new ActiveRoomStore(directory.resolve("active-room"));

        store.save("bcd fgh");

        assertEquals(Optional.of("BCDFGH"), store.read());
    }

    @Test
    void refusesACodeThatCouldNeverBeARoom() {
        ActiveRoomStore store = new ActiveRoomStore(directory.resolve("active-room"));

        assertFalse(store.save("not-a-room-code-at-all"));
        assertEquals(Optional.empty(), store.read());
    }

    @Test
    void ignoresAFileSomebodyHasEdited() throws Exception {
        //Normalised on the way out as well as in: a file left by an older build,
        //or edited by hand, must not become a request for an impossible room.
        Path file = directory.resolve("active-room");
        Files.writeString(file, "nonsense that is not a code");

        assertEquals(Optional.empty(), new ActiveRoomStore(file).read());
    }

    @Test
    void treatsAnUnreadableStoreAsHavingNoGame() {
        //A directory where the file should be. Failing to read must not stop
        //the application starting — the offer is a convenience.
        ActiveRoomStore store = new ActiveRoomStore(directory);

        assertEquals(Optional.empty(), store.read());
        assertFalse(store.save("BCDFGH"));
    }
}
