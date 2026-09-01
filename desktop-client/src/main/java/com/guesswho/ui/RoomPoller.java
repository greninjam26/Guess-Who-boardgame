package com.guesswho.ui;

import com.guesswho.client.OnlineGameClient;
import com.guesswho.client.OnlineOutcome;
import com.guesswho.room.RoomState;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * Asks the server what has happened, over and over, while an online game is on
 * screen.
 *
 * <p>Polling rather than a push connection, because a turn-based game changes
 * a few times a minute and a socket would be more to keep alive than the game
 * is worth. A player waiting on an opponent is the only one who needs this at
 * all.</p>
 */
class RoomPoller {
    /** Told what the server said, always on the interface thread. */
    interface Listener {
        /**
         * @param state the game as this player may see it
         */
        void updated(RoomState state);

        /**
         * @param outcome why the game could not be read
         */
        void failed(OnlineOutcome<RoomState> outcome);
    }

    /** Often enough to feel live, rarely enough to be unnoticeable. */
    private static final long INTERVAL_SECONDS = 2;

    private final OnlineGameClient client;
    //One request at a time. A slow server must not leave a queue of polls
    //arriving all at once when it recovers.
    private final AtomicBoolean inFlight = new AtomicBoolean();

    private ScheduledExecutorService scheduler;
    private String code;
    private String token;
    private Listener listener;

    /**
     * @param client talks to the server
     */
    RoomPoller(OnlineGameClient client) {
        this.client = client;
    }

    /**
     * Starts asking about a room.
     *
     * @param code     the room's code
     * @param token    the session token
     * @param listener told what comes back
     */
    void start(String code, String token, Listener listener) {
        stop();
        this.code = code;
        this.token = token;
        this.listener = listener;
        //A daemon thread, so a poller nobody stopped cannot keep the
        //application alive after its window has gone.
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "room-poller");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                this::pollOnce, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Stops asking.
     *
     * <p>Called when the game ends, when the window closes, and before starting
     * on another room. Polling one game while showing another would put the
     * wrong board on screen.</p>
     */
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        listener = null;
        inFlight.set(false);
    }

    /** Whether it is currently asking about anything. */
    boolean isPolling() {
        return scheduler != null;
    }

    /**
     * Asks once.
     *
     * <p>Visible for testing, and used by the schedule. Returns immediately if
     * a previous request has not come back.</p>
     */
    void pollOnce() {
        Listener told = listener;
        if (told == null || !inFlight.compareAndSet(false, true)) {
            return;
        }
        client.state(code, token).whenComplete((outcome, failure) -> {
            inFlight.set(false);
            if (listener != told) {
                //Stopped, or moved on to another room, while this was in
                //flight. Whatever came back is about a game nobody is looking
                //at now.
                return;
            }
            onInterfaceThread(() -> {
                if (failure != null) {
                    told.failed(OnlineOutcome.failed(OnlineOutcome.Kind.UNREACHABLE,
                            "The server could not be reached"));
                    return;
                }
                if (outcome.isOk()) {
                    told.updated(outcome.value());
                    return;
                }
                told.failed(outcome);
            });
        });
    }

    /** Swing may only be touched from its own thread, and this arrives on another. */
    private static void onInterfaceThread(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
        }
        else {
            SwingUtilities.invokeLater(work);
        }
    }
}
