package com.guesswho.ui;

import com.guesswho.client.OnlineGameClient;
import com.guesswho.client.OnlineOutcome;
import com.guesswho.room.Room;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Drives an online game: which room, what the server last said, and what to do
 * about it.
 *
 * <p>Separate from {@link GUI} because online play has a lifecycle that local
 * play does not — a room to open or join, a poll to keep running, and a set of
 * failures that mean different things. Putting it in the frame would take
 * {@code GUI} back towards the thing Phase 02 pulled apart.</p>
 */
class OnlineGameController {
    /** Told what to show. Every call arrives on the interface thread. */
    interface View {
        /**
         * @param room the room just opened, whose code needs sharing
         */
        void roomOpened(Room room);

        /**
         * @param state the game as this player may see it
         */
        void stateChanged(RoomState state);

        /**
         * @param message what went wrong, in words a player can act on
         */
        void problem(String message);

        /**
         * The server has stopped answering, and the game is waiting for it.
         *
         * <p>Reported once when it starts, not once per failed poll. A client
         * polls every couple of seconds, so anything that interrupts a
         * connection produces a run of identical failures, and treating each as
         * news gave the player a modal dialog every two seconds until their
         * network came back.</p>
         *
         * <p>Not an error the player can act on. Their game is still there, the
         * turn clock allows for a lapse this long, and the honest thing to show
         * is that the client is still trying.</p>
         */
        void connectionLost();

        /** The server is answering again, and the game has caught up. */
        void connectionRestored();

        /**
         * The room is gone for good — expired, or swept after being abandoned.
         *
         * <p>Terminal, unlike a connection failure: there is nothing left to
         * poll, and continuing to ask produces the same answer for ever.</p>
         *
         * @param message what to tell the player
         */
        void gameGone(String message);

        /** The session is no longer good and the player has to sign in again. */
        void signedOut();
    }

    private final OnlineGameClient client;
    private final RoomPoller poller;
    private final Supplier<String> token;

    private View view;
    private String code;
    private RoomState state;
    //Whether the server is currently failing to answer, so that a run of
    //identical failures is reported once rather than on every poll.
    private boolean offline;

    /**
     * @param client talks to the server
     * @param poller keeps asking what has happened
     * @param token  supplies the session token, which can change
     */
    OnlineGameController(OnlineGameClient client, RoomPoller poller, Supplier<String> token) {
        this.client = client;
        this.poller = poller;
        this.token = token;
    }

    /**
     * Opens a room and starts waiting for somebody to join it.
     *
     * @param view told what to show
     */
    void createRoom(View view) {
        this.view = view;
        handle(client.createRoom(token.get()), room -> {
            code = room.code();
            view.roomOpened(room);
            beginPolling();
        });
    }

    /**
     * Joins somebody else's room.
     *
     * @param typedCode the code they shared
     * @param view      told what to show
     */
    void joinRoom(String typedCode, View view) {
        this.view = view;
        handle(client.joinRoom(typedCode, token.get()), room -> {
            code = room.code();
            view.roomOpened(room);
            beginPolling();
        });
    }

    /**
     * Picks a game back up in a room this client is already one of the two
     * players in.
     *
     * <p>Not {@link #joinRoom}, which asks the server to put somebody into a
     * waiting room and is refused for a game already under way — including by
     * the player who is in it. Rejoining adds nobody: the server has held the
     * room, the game and both accounts all along, so this only has to start
     * asking again, and the first poll brings back whatever the game has become
     * in the meantime.</p>
     *
     * <p>Whether the room is still there is not checked first. That would be a
     * request on every launch to answer a question the first poll answers
     * anyway, and a room that has gone already has somewhere to arrive: the
     * poll reports it, and the game-gone screen says so.</p>
     *
     * @param roomCode the room to pick back up
     * @param view     told what to show
     */
    void rejoin(String roomCode, View view) {
        this.view = view;
        this.code = roomCode;
        beginPolling();
    }

    /**
     * Chooses the character this player will be guessed at.
     *
     * @param character the character they are holding
     */
    void chooseCharacter(String character) {
        move(client.chooseCharacter(code, character, token.get()));
    }

    /**
     * Asks the opponent a question.
     *
     * @param question what to ask
     */
    void ask(String question) {
        move(client.ask(code, question, token.get()));
    }

    /**
     * Answers the question the opponent asked.
     *
     * @param answer yes or no
     */
    void answer(boolean answer) {
        move(client.answer(code, answer, token.get()));
    }

    /**
     * Guesses the opponent's character, which ends the game either way.
     *
     * @param character who they think it is
     */
    void guess(String character) {
        move(client.guess(code, character, token.get()));
    }

    /**
     * Sends further updates somewhere else.
     *
     * <p>The room screen watches until there is a game; the board watches from
     * then on. Without this the room screen would keep receiving states it has
     * already handed over.</p>
     *
     * @param next told what to show from now on
     */
    void showOn(View next) {
        this.view = next;
    }

    /** Leaves the game, and stops asking about it. */
    void leave() {
        poller.stop();
        view = null;
        code = null;
        state = null;
        //Cleared with everything else, or the next game would start believing
        //the server was already unreachable and never say when it came back.
        offline = false;
    }

    /**
     * The game as the server last described it.
     *
     * @return the last state, or null before there is one
     */
    RoomState state() {
        return state;
    }

    /**
     * The code to share, once there is a room.
     *
     * @return the room's code, or null when not in one
     */
    String code() {
        return code;
    }

    private void beginPolling() {
        poller.start(code, token.get(), new RoomPoller.Listener() {
            @Override
            public void updated(RoomState updated) {
                apply(updated);
            }

            @Override
            public void failed(OnlineOutcome<RoomState> outcome) {
                report(outcome);
            }
        });
    }

    /**
     * Applies a move, and shows what it produced without waiting for a poll.
     *
     * <p>The server answers every move with the state it left behind, so the
     * player who moved sees it at once. Polling is for the opponent's moves,
     * which is the only thing this client cannot know about already.</p>
     */
    private void move(CompletableFuture<OnlineOutcome<RoomState>> request) {
        handle(request, this::apply);
    }

    private void apply(RoomState updated) {
        //Any answer at all means the server is back, whatever it says.
        markOnline();
        state = updated;
        if (view != null) {
            view.stateChanged(updated);
        }
        if (updated.status() == RoomStatus.FINISHED) {
            //Nothing more will change. Polling a finished game is asking a
            //question whose answer cannot move again.
            poller.stop();
        }
    }

    private <T> void handle(CompletableFuture<OnlineOutcome<T>> request,
            java.util.function.Consumer<T> onSuccess) {
        request.whenComplete((outcome, failure) -> onInterfaceThread(() -> {
            View told = view;
            if (told == null) {
                //Left the game while this was in flight.
                return;
            }
            if (failure != null) {
                //A one-shot request the player just made — opening a room,
                //joining one, playing a move. Nothing retries it, so this is
                //told plainly rather than shown as reconnecting: a banner
                //promising recovery would be a promise nothing here keeps. The
                //poll path is the one that is still trying, and it reports
                //through connectionLost instead.
                told.problem("The server could not be reached.");
                return;
            }
            if (outcome.isOk()) {
                onSuccess.accept(outcome.value());
                return;
            }
            report(outcome);
        }));
    }

    private void report(OnlineOutcome<?> outcome) {
        View told = view;
        if (told == null) {
            return;
        }
        if (outcome.kind() == OnlineOutcome.Kind.UNREACHABLE) {
            //Not a problem to interrupt anybody with. The game is still there,
            //the client is still trying, and a dialog every two seconds is the
            //worst possible way to say so.
            markOffline();
            return;
        }
        markOnline();
        if (outcome.kind() == OnlineOutcome.Kind.SIGNED_OUT) {
            //Not something to do differently in the game: they have to sign in
            //again, and polling on a dead token would only repeat the message.
            poller.stop();
            told.signedOut();
            return;
        }
        if (outcome.kind() == OnlineOutcome.Kind.NOT_FOUND && code != null) {
            //Mid-game this means the room has expired or been swept, which is
            //not something more polling can recover from. Before a game, the
            //same answer means a mistyped code, which is why this only applies
            //once we are in a room.
            poller.stop();
            told.gameGone("This game is no longer available. It may have expired.");
            return;
        }
        told.problem(outcome.message());
    }

    /** Notes that the server has stopped answering, telling the view once. */
    private void markOffline() {
        if (offline) {
            return;
        }
        offline = true;
        View told = view;
        if (told != null) {
            told.connectionLost();
        }
    }

    /** Notes that the server is answering again, telling the view once. */
    private void markOnline() {
        if (!offline) {
            return;
        }
        offline = false;
        View told = view;
        if (told != null) {
            told.connectionRestored();
        }
    }

    private static void onInterfaceThread(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
        }
        else {
            SwingUtilities.invokeLater(work);
        }
    }
}
