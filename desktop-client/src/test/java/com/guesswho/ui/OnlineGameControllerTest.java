package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.client.OnlineGameClient;
import com.guesswho.client.OnlineOutcome;
import com.guesswho.room.Room;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnlineGameControllerTest {
    private final List<Room> opened = new ArrayList<>();
    private final List<RoomState> shown = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final List<String> signedOut = new ArrayList<>();
    //Recorded as a sequence, because the point of these is that they arrive on
    //transitions rather than on every poll.
    private final List<String> connection = new ArrayList<>();
    private final List<String> gone = new ArrayList<>();
    private final List<com.guesswho.room.GameReveal> revealed = new ArrayList<>();

    private final OnlineGameController.View view = new OnlineGameController.View() {
        @Override
        public void roomOpened(Room room) {
            opened.add(room);
        }

        @Override
        public void stateChanged(RoomState state) {
            shown.add(state);
        }

        @Override
        public void problem(String message) {
            problems.add(message);
        }

        @Override
        public void connectionLost() {
            connection.add("lost");
        }

        @Override
        public void connectionRestored() {
            connection.add("restored");
        }

        @Override
        public void cannotContinue(String message) {
            gone.add(message);
        }

        @Override
        public void revealed(com.guesswho.room.GameReveal reveal) {
            revealed.add(reveal);
        }

        @Override
        public void signedOut() {
            signedOut.add("signed out");
        }
    };

    private FakeClient client;
    private RecordingPoller poller;
    private OnlineGameController controller;

    @BeforeEach
    void freshController() {
        client = new FakeClient();
        poller = new RecordingPoller();
        controller = new OnlineGameController(client, poller, () -> "a-token");
    }

    @Test
    void opensARoomAndStartsWaitingForSomebody() throws Exception {
        controller.createRoom(view);
        settle();

        assertEquals(1, opened.size());
        assertEquals("BCDFGH", controller.code());
        assertTrue(poller.started, "Nothing would notice the opponent arriving");
    }

    @Test
    void joinsSomebodyElsesRoom() throws Exception {
        controller.joinRoom("bcd fgh", view);
        settle();

        assertEquals(1, opened.size());
        assertTrue(poller.started);
    }

    @Test
    void showsWhatAMoveProducedWithoutWaitingForAPoll() throws Exception {
        //The server answers every move with the state it left behind, so the
        //player who moved should not watch a spinner until the next poll.
        controller.createRoom(view);
        settle();
        shown.clear();

        controller.ask("Does your character wear glasses?");
        settle();

        assertEquals(1, shown.size());
    }

    @Test
    void stopsAskingOnceTheGameIsOver() throws Exception {
        controller.createRoom(view);
        settle();
        client.next = OnlineOutcome.ok(roomState(RoomStatus.FINISHED));

        controller.guess("Sam");
        settle();

        assertTrue(poller.stopped, "Polling a finished game asks a question with one answer");
    }

    @Test
    void tellsThePlayerWhenTheRulesRefusedAMove() throws Exception {
        controller.createRoom(view);
        settle();
        client.next = OnlineOutcome.failed(OnlineOutcome.Kind.REFUSED, "It is not your turn");

        controller.ask("Does your character wear glasses?");
        settle();

        assertEquals(List.of("It is not your turn"), problems);
    }

    @Test
    void treatsBeingSignedOutAsSomethingOtherThanAGameProblem() throws Exception {
        //Signing in again is the remedy, not doing something different in the
        //game, and polling on a dead token would only repeat the message.
        controller.createRoom(view);
        settle();
        client.next = OnlineOutcome.failed(OnlineOutcome.Kind.SIGNED_OUT, "Sign in again");

        controller.ask("Does your character wear glasses?");
        settle();

        assertEquals(1, signedOut.size());
        assertTrue(problems.isEmpty());
        assertTrue(poller.stopped);
    }

    @Test
    void reportsAnUnreachableServerRatherThanGoingQuiet() throws Exception {
        client.fail = true;

        controller.createRoom(view);
        settle();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("could not be reached"));
    }

    @Test
    void saysNothingAfterLeaving() throws Exception {
        //A reply that arrives after leaving is about a game nobody is looking
        //at, and showing it would put the wrong board on screen.
        CompletableFuture<OnlineOutcome<Room>> pending = new CompletableFuture<>();
        client.roomReply = pending;
        controller.createRoom(view);

        controller.leave();
        pending.complete(OnlineOutcome.ok(room()));
        settle();

        assertTrue(opened.isEmpty());
        assertTrue(poller.stopped);
    }

    @Test
    void forgetsTheRoomOnLeaving() throws Exception {
        controller.createRoom(view);
        settle();

        controller.leave();

        assertTrue(controller.code() == null);
        assertTrue(controller.state() == null);
    }

    @Test
    void keepsTheLastStateForWhoeverNeedsIt() throws Exception {
        controller.createRoom(view);
        settle();

        controller.ask("Does your character wear glasses?");
        settle();

        assertEquals(RoomStatus.IN_PROGRESS, controller.state().status());
    }

    @Test
    void passesAnOpponentsMoveOnWhenThePollFindsIt() throws Exception {
        controller.createRoom(view);
        settle();
        shown.clear();

        poller.listener.updated(roomState(RoomStatus.IN_PROGRESS));

        assertEquals(1, shown.size());
    }

    // --- helpers -------------------------------------------------------

    private void settle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static Room room() {
        return new Room("BCDFGH", RoomStatus.WAITING, "host", null,
                Instant.now().plusSeconds(600));
    }

    private static RoomState roomState(RoomStatus status) {
        return RoomState.builder()
                .code("BCDFGH")
                .status(status)
                .you("host")
                .opponent("guest")
                .yourCharacter("Olivia")
                .opponentHasChosen(true)
                .opponentPresent(true)
                .yourTurn(true)
                .currentPlayer("host")
                .winner(status == RoomStatus.FINISHED ? "host" : null)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }

    /** A poller that records what it was told to do rather than doing it. */
    @Test
    void picksAGameBackUpWithoutAskingToJoinItAgain() throws Exception {
        //Rejoining adds nobody. The server has held the room, the game and both
        //accounts all along, so asking to join would be refused — the room is
        //not waiting for anybody, it is being played.
        controller.rejoin("BCDFGH", view);
        settle();

        assertTrue(poller.started, "A rejoined game has to start asking again");
        assertEquals(0, client.joinRequests,
                "Rejoining must not ask the server to put anybody into the room");
        assertEquals("BCDFGH", controller.code());
    }

    @Test
    void showsWhateverTheGameBecameWhileTheClientWasAway() throws Exception {
        controller.rejoin("BCDFGH", view);
        settle();

        poller.listener.updated(roomState(RoomStatus.IN_PROGRESS));

        assertEquals(1, shown.size(), "The first poll is what puts the game back on screen");
    }

    @Test
    void endsTheGameWhenTheRoomExpiredWhileTheClientWasAway() throws Exception {
        //The case rejoining has to handle: the room was swept while the
        //application was shut, so there is nothing to come back to.
        controller.rejoin("BCDFGH", view);
        settle();

        poller.listener.failed(OnlineOutcome.failed(
                OnlineOutcome.Kind.NOT_FOUND, "No game with that code"));

        assertEquals(1, gone.size());
        assertTrue(poller.stopped);
    }

    @Test
    void asksForTheEndingOnceTheGameIsOver() throws Exception {
        //A second request, because the state a player reads during a game is
        //built so it cannot carry the opponent's character.
        joinedGame();

        poller.listener.updated(roomState(RoomStatus.FINISHED));
        settle();

        assertEquals(1, revealed.size(), "The ending should be asked for and shown");
        assertEquals("Sam", revealed.get(0).opponent().character());
    }

    @Test
    void doesNotAskForAnEndingWhileTheGameIsStillOn() throws Exception {
        joinedGame();

        poller.listener.updated(roomState(RoomStatus.IN_PROGRESS));
        settle();

        assertEquals(0, client.revealRequests,
                "Asking mid-game is a request the server refuses anyway");
    }

    @Test
    void stillShowsTheResultWhenTheEndingCannotBeLoaded() throws Exception {
        //The game is already decided and on screen. A player who cannot load the
        //reveal should see the result without the portraits, not an error over
        //the top of it.
        joinedGame();
        client.nextReveal = OnlineOutcome.failed(
                OnlineOutcome.Kind.UNREACHABLE, "The server could not be reached");

        poller.listener.updated(roomState(RoomStatus.FINISHED));
        settle();

        assertEquals(0, revealed.size());
        assertEquals(List.of(), problems, "A missing reveal is not worth interrupting over");
    }

    /** A controller in a joined room, with the poller running. */
    private void joinedGame() throws Exception {
        controller.joinRoom("bcd fgh", view);
        settle();
    }

    @Test
    void reportsARunOfFailedPollsOnceRatherThanEveryTime() throws Exception {
        //The bug this closes: every failed poll raised a problem, and the frame
        //turned each into a modal dialog. A client polling every two seconds
        //gave the player a dialog every two seconds for as long as their
        //network was down, each one titled "Invalid game setup".
        joinedGame();

        for (int poll = 0; poll < 5; poll++) {
            poller.listener.failed(OnlineOutcome.failed(
                    OnlineOutcome.Kind.UNREACHABLE, "The server could not be reached"));
        }

        assertEquals(List.of("lost"), connection);
        assertEquals(List.of(), problems, "A dropped connection is not a problem to interrupt over");
    }

    @Test
    void saysWhenTheConnectionComesBack() throws Exception {
        joinedGame();
        poller.listener.failed(OnlineOutcome.failed(
                OnlineOutcome.Kind.UNREACHABLE, "The server could not be reached"));

        poller.listener.updated(roomState(RoomStatus.IN_PROGRESS));

        assertEquals(List.of("lost", "restored"), connection);
    }

    @Test
    void keepsPollingThroughALapseSoTheGameCanRecover() throws Exception {
        //A connection failure is not terminal. Stopping here would mean a client
        //that never came back on its own, which is the opposite of reconnecting.
        joinedGame();

        poller.listener.failed(OnlineOutcome.failed(
                OnlineOutcome.Kind.UNREACHABLE, "The server could not be reached"));

        assertFalse(poller.stopped, "A lapse must not stop the client trying");
    }

    @Test
    void treatsARoomThatHasGoneAsTheEndOfTheGame() throws Exception {
        //Mid-game a missing room means it expired or was swept. More polling
        //returns the same answer for ever, so this one is terminal.
        joinedGame();

        poller.listener.failed(OnlineOutcome.failed(
                OnlineOutcome.Kind.NOT_FOUND, "No game with that code"));

        assertEquals(1, gone.size(), "The player should be told the game is gone");
        assertTrue(poller.stopped, "Nothing is left to poll for");
    }

    @Test
    void forgetsAConnectionLapseWhenLeavingTheGame() throws Exception {
        //Otherwise the next game starts believing the server is already
        //unreachable, and never reports it coming back.
        joinedGame();
        poller.listener.failed(OnlineOutcome.failed(
                OnlineOutcome.Kind.UNREACHABLE, "The server could not be reached"));
        controller.leave();
        connection.clear();

        joinedGame();
        poller.listener.failed(OnlineOutcome.failed(
                OnlineOutcome.Kind.UNREACHABLE, "The server could not be reached"));

        assertEquals(List.of("lost"), connection);
    }

    private static final class RecordingPoller extends RoomPoller {
        private boolean started;
        private boolean stopped;
        private Listener listener;

        private RecordingPoller() {
            super(null);
        }

        @Override
        void start(String code, String token, Listener listener) {
            started = true;
            stopped = false;
            this.listener = listener;
        }

        @Override
        void stop() {
            stopped = true;
        }
    }

    /** A client that answers whatever the test last set. */
    private final class FakeClient implements OnlineGameClient {
        private OnlineOutcome<RoomState> next = OnlineOutcome.ok(roomState(RoomStatus.IN_PROGRESS));
        private CompletableFuture<OnlineOutcome<Room>> roomReply;
        private boolean fail;
        //Counted so a test can show that rejoining does not ask to join.
        private int joinRequests;
        private int revealRequests;
        private OnlineOutcome<com.guesswho.room.GameReveal> nextReveal =
                OnlineOutcome.ok(new com.guesswho.room.GameReveal("BCDFGH", "host",
                        new com.guesswho.room.GameReveal.Verified(
                                "host", "Olivia", true, true, List.of()),
                        new com.guesswho.room.GameReveal.Verified(
                                "guest", "Sam", true, true, List.of())));

        private CompletableFuture<OnlineOutcome<Room>> roomOutcome() {

            if (fail) {
                return CompletableFuture.failedFuture(new java.io.IOException("refused"));
            }
            return roomReply != null
                    ? roomReply
                    : CompletableFuture.completedFuture(OnlineOutcome.ok(room()));
        }

        private CompletableFuture<OnlineOutcome<RoomState>> stateOutcome() {
            if (fail) {
                return CompletableFuture.failedFuture(new java.io.IOException("refused"));
            }
            return CompletableFuture.completedFuture(next);
        }

        @Override
        public CompletableFuture<OnlineOutcome<Room>> createRoom(String token) {
            return roomOutcome();
        }

        @Override
        public CompletableFuture<OnlineOutcome<Room>> joinRoom(String code, String token) {
            joinRequests++;
            return roomOutcome();
        }

        @Override
        public CompletableFuture<OnlineOutcome<com.guesswho.room.GameReveal>> reveal(
                String code, String token) {
            revealRequests++;
            return CompletableFuture.completedFuture(nextReveal);
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> state(String code, String token) {
            return stateOutcome();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> chooseCharacter(
                String code, String character, String token) {
            return stateOutcome();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> ask(
                String code, String question, String token) {
            return stateOutcome();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> answer(
                String code, boolean answer, String token) {
            return stateOutcome();
        }

        @Override
        public CompletableFuture<OnlineOutcome<RoomState>> guess(
                String code, String character, String token) {
            return stateOutcome();
        }
    }
}
