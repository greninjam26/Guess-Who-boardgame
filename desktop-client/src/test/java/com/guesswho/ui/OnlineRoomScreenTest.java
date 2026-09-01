package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.client.OnlineGameClient;
import com.guesswho.client.OnlineOutcome;
import com.guesswho.room.Room;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnlineRoomScreenTest {
    private final List<String> joined = new ArrayList<>();
    private final List<RoomState> ready = new ArrayList<>();
    private final List<String> back = new ArrayList<>();

    private OnlineRoomScreen screen;

    @BeforeEach
    void freshScreen() throws Exception {
        joined.clear();
        ready.clear();
        back.clear();
        OnlineGameController controller = new OnlineGameController(
                clientThatRecordsJoins(), new NoPoller(), () -> "a-token");
        SwingUtilities.invokeAndWait(() -> {
            screen = new OnlineRoomScreen(controller, ready::add, () -> back.add("back"));
            screen.begin();
        });
    }

    @Test
    void showsTheCodeToShareOnceARoomIsOpen() throws Exception {
        SwingUtilities.invokeAndWait(() -> screen.roomOpened(room(null)));

        assertTrue(visibleText().contains("BCDFGH"),
                "The code is the whole of how somebody joins: " + visibleText());
        assertTrue(visibleText().contains("Waiting for somebody"));
    }

    @Test
    void handsOverOnceThereIsAGameToPlay() throws Exception {
        SwingUtilities.invokeAndWait(() -> screen.roomOpened(room(null)));

        SwingUtilities.invokeAndWait(() -> screen.stateChanged(roomState(RoomStatus.IN_PROGRESS)));

        assertEquals(1, ready.size());
    }

    @Test
    void doesNotHandOverTwice() throws Exception {
        //Every poll sends the state again. Rebuilding the board underneath
        //somebody mid-move would lose whatever they were doing.
        SwingUtilities.invokeAndWait(() -> screen.roomOpened(room(null)));

        SwingUtilities.invokeAndWait(() -> {
            screen.stateChanged(roomState(RoomStatus.IN_PROGRESS));
            screen.stateChanged(roomState(RoomStatus.IN_PROGRESS));
            screen.stateChanged(roomState(RoomStatus.IN_PROGRESS));
        });

        assertEquals(1, ready.size());
    }

    @Test
    void keepsWaitingWhileNobodyHasJoined() throws Exception {
        SwingUtilities.invokeAndWait(() -> screen.roomOpened(room(null)));

        SwingUtilities.invokeAndWait(() -> screen.stateChanged(roomState(RoomStatus.WAITING)));

        assertTrue(ready.isEmpty());
        assertTrue(visibleText().contains("Waiting for somebody"));
    }

    @Test
    void joinsWithTheCodeAsItWasTyped() throws Exception {
        //Typed off another screen, so lower case and a space in the middle are
        //normal rather than a mistake. The server tidies it.
        SwingUtilities.invokeAndWait(() -> button("Join with a code").doClick());
        SwingUtilities.invokeAndWait(() -> field().setText("bcd fgh"));

        SwingUtilities.invokeAndWait(() -> button("Join").doClick());

        assertEquals(List.of("bcd fgh"), joined);
    }

    @Test
    void refusesToJoinWithAnEmptyCode() throws Exception {
        SwingUtilities.invokeAndWait(() -> button("Join with a code").doClick());

        SwingUtilities.invokeAndWait(() -> button("Join").doClick());

        assertTrue(joined.isEmpty());
        assertTrue(visibleText().contains("Enter the code"));
    }

    @Test
    void saysWhatWentWrong() throws Exception {
        SwingUtilities.invokeAndWait(() -> screen.problem("You already have 5 games open."));

        assertTrue(visibleText().contains("5 games open"));
    }

    @Test
    void saysWhenTheSessionHasGone() throws Exception {
        SwingUtilities.invokeAndWait(() -> screen.signedOut());

        assertTrue(visibleText().contains("signed out"));
    }

    @Test
    void leavesOnlinePlayAltogether() throws Exception {
        SwingUtilities.invokeAndWait(() -> button("Back").doClick());

        assertEquals(List.of("back"), back);
    }

    @Test
    void startsAgainAtTheChoiceEachTime() throws Exception {
        SwingUtilities.invokeAndWait(() -> screen.roomOpened(room(null)));

        SwingUtilities.invokeAndWait(() -> screen.begin());

        assertTrue(visibleText().contains("Play against a friend"));
    }

    // --- helpers -------------------------------------------------------

    /** Everything currently on screen, since the cards hide the rest. */
    private String visibleText() {
        StringBuilder text = new StringBuilder();
        collect(screen.panel(), text);
        return text.toString();
    }

    private void collect(Container container, StringBuilder text) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child instanceof JLabel label) {
                text.append(label.getText()).append(' ');
            }
            if (child instanceof JButton button) {
                text.append(button.getText()).append(' ');
            }
            if (child instanceof Container nested) {
                collect(nested, text);
            }
        }
    }

    private JButton button(String label) {
        JButton found = find(screen.panel(), JButton.class, label);
        if (found == null) {
            throw new AssertionError("No visible button labelled " + label);
        }
        return found;
    }

    private JTextField field() {
        return find(screen.panel(), JTextField.class, null);
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> T find(Container container, Class<T> type, String label) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (type.isInstance(child)
                    && (label == null || label.equals(((JButton) child).getText()))) {
                return (T) child;
            }
            if (child instanceof JPanel nested) {
                T found = find(nested, type, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Room room(String guest) {
        return new Room("BCDFGH", guest == null ? RoomStatus.WAITING : RoomStatus.IN_PROGRESS,
                "host", guest, Instant.now().plusSeconds(600));
    }

    private static RoomState roomState(RoomStatus status) {
        return new RoomState("BCDFGH", status, "host", "guest", null, false, true,
                "host", null, null, List.of(), List.of(), null,
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

    private OnlineGameClient clientThatRecordsJoins() {
        return new OnlineGameClient() {
            @Override
            public CompletableFuture<OnlineOutcome<Room>> createRoom(String token) {
                return CompletableFuture.completedFuture(OnlineOutcome.ok(room(null)));
            }

            @Override
            public CompletableFuture<OnlineOutcome<Room>> joinRoom(String code, String token) {
                joined.add(code);
                return CompletableFuture.completedFuture(OnlineOutcome.ok(room("guest")));
            }

            @Override
            public CompletableFuture<OnlineOutcome<RoomState>> state(String code, String token) {
                return CompletableFuture.completedFuture(
                        OnlineOutcome.ok(roomState(RoomStatus.IN_PROGRESS)));
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
        };
    }
}
