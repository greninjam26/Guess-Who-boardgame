package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.client.OnlineGameClient;
import com.guesswho.client.OnlineOutcome;
import com.guesswho.game.Board;
import com.guesswho.room.Room;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnlineGameScreensTest {
    private static CharacterImages images;
    private static Board board;

    private OnlineGameScreens screens;

    @BeforeAll
    static void loadResources() throws Exception {
        AtomicReference<CharacterImages> loaded = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> loaded.set(new CharacterImages()));
        images = loaded.get();
        board = new Board();
    }

    @BeforeEach
    void freshScreens() throws Exception {
        OnlineGameController controller = new OnlineGameController(
                new UnusedClient(), new NoPoller(), () -> "a-token");
        SwingUtilities.invokeAndWait(() -> screens = new OnlineGameScreens(
                controller, images, board, false, () -> {
                }));
    }

    @Test
    void asksForACharacterBeforeAnythingElse() throws Exception {
        show(state(RoomStatus.IN_PROGRESS, null, List.of(), List.of(), null));

        assertTrue(visibleText().contains("Choose the character"));
    }

    @Test
    void showsTheBoardOnceACharacterIsChosen() throws Exception {
        show(state(RoomStatus.IN_PROGRESS, "Olivia", List.of(), List.of(), null));

        assertTrue(visibleText().contains("Your turn"), visibleText());
    }

    @Test
    void saysWhoWonAtTheEnd() throws Exception {
        show(state(RoomStatus.FINISHED, "Olivia", List.of(), List.of(), "host"));
        assertTrue(visibleText().contains("You won"));

        show(state(RoomStatus.FINISHED, "Olivia", List.of(), List.of(), "guest"));
        assertTrue(visibleText().contains("guest won"));
    }

    @Test
    void writesBothSidesOfTheTranscript() throws Exception {
        show(state(RoomStatus.IN_PROGRESS, "Olivia",
                List.of(new RoomState.AskedQuestion("Do they wear glasses?", true)),
                List.of(new RoomState.AskedQuestion("Do they have a hat?", false)),
                null));

        String shown = visibleText();
        assertTrue(shown.contains("Do they wear glasses?"), shown);
        assertTrue(shown.contains("Yes"), shown);
        assertTrue(shown.contains("Do they have a hat?"), shown);
        assertTrue(shown.contains("No"), shown);
    }

    @Test
    void doesNotDoubleTheTranscriptWhenTheSameStateArrivesAgain() throws Exception {
        //Every poll carries the whole history. Appending rather than rewriting
        //would repeat every line every two seconds.
        RoomState repeated = state(RoomStatus.IN_PROGRESS, "Olivia",
                List.of(new RoomState.AskedQuestion("Do they wear glasses?", true)),
                List.of(), null);

        show(repeated);
        show(repeated);
        show(repeated);

        assertEquals(1, occurrences(visibleText(), "Do they wear glasses?"),
                "The transcript repeated itself: " + visibleText());
    }

    @Test
    void growsTheTranscriptAsTheGameGoesOn() throws Exception {
        show(state(RoomStatus.IN_PROGRESS, "Olivia",
                List.of(new RoomState.AskedQuestion("Do they wear glasses?", true)),
                List.of(), null));

        show(state(RoomStatus.IN_PROGRESS, "Olivia",
                List.of(new RoomState.AskedQuestion("Do they wear glasses?", true),
                        new RoomState.AskedQuestion("Do they have a hat?", false)),
                List.of(), null));

        assertEquals(1, occurrences(visibleText(), "Do they wear glasses?"));
        assertEquals(1, occurrences(visibleText(), "Do they have a hat?"));
    }

    @Test
    void showsEveryStateWithoutFalling() throws Exception {
        //Driven entirely by the server, so a state it mishandles should put the
        //wrong thing on screen rather than throw in front of a player.
        for (RoomState next : List.of(
                state(RoomStatus.WAITING, null, List.of(), List.of(), null),
                state(RoomStatus.IN_PROGRESS, null, List.of(), List.of(), null),
                state(RoomStatus.IN_PROGRESS, "Olivia", List.of(), List.of(), null),
                state(RoomStatus.FINISHED, "Olivia", List.of(), List.of(), "host"))) {
            show(next);
        }
    }

    // --- helpers -------------------------------------------------------

    private void show(RoomState state) throws Exception {
        SwingUtilities.invokeAndWait(() -> screens.show(state));
    }

    private String visibleText() {
        StringBuilder text = new StringBuilder();
        collect(screens.panel(), text);
        return text.toString();
    }

    private void collect(Container container, StringBuilder text) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof JLabel label && label.getText() != null) {
                text.append(label.getText()).append(' ');
            }
            if (child instanceof javax.swing.JButton button) {
                text.append(button.getText()).append(' ');
            }
            if (child instanceof Container nested) {
                collect(nested, text);
            }
        }
    }

    private static int occurrences(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static RoomState state(RoomStatus status, String yourCharacter,
            List<RoomState.AskedQuestion> yours, List<RoomState.AskedQuestion> theirs,
            String winner) {
        return new RoomState("BCDFGH", status, "host", "guest", yourCharacter,
                true, true, "host", null, null, yours, theirs, winner,
                Instant.now().plusSeconds(600));
    }

    /** A poller that does nothing, so no background thread runs during a test. */
    private static final class NoPoller extends RoomPoller {
        private NoPoller() {
            super(null);
        }

        @Override
        void start(String code, String token, Listener listener) {
        }

        @Override
        void stop() {
        }
    }

    /** Nothing in these tests makes a move, so nothing should reach the server. */
    private static final class UnusedClient implements OnlineGameClient {
        @Override
        public CompletableFuture<OnlineOutcome<Room>> createRoom(String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OnlineOutcome<Room>> joinRoom(String code, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> state(String code, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> chooseCharacter(
                String code, String character, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> ask(
                String code, String question, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> answer(
                String code, boolean answer, String token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> guess(
                String code, String character, String token) {
            throw new UnsupportedOperationException();
        }
    }
}
