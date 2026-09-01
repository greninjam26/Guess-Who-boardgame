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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class RoomPollerTest {
    private final List<RoomState> seen = new ArrayList<>();
    private final List<OnlineOutcome<RoomState>> failures = new ArrayList<>();
    private final AtomicInteger requests = new AtomicInteger();

    private final RoomPoller.Listener listener = new RoomPoller.Listener() {
        @Override
        public void updated(RoomState state) {
            seen.add(state);
        }

        @Override
        public void failed(OnlineOutcome<RoomState> outcome) {
            failures.add(outcome);
        }
    };

    @Test
    void reportsWhatTheServerSays() throws Exception {
        RoomPoller poller = new RoomPoller(alwaysReturning(OnlineOutcome.ok(state())));
        poller.start("BCDFGH", "a-token", listener);

        settle();

        assertEquals(1, seen.size());
        assertEquals("host", seen.get(0).you());
        poller.stop();
    }

    @Test
    void deliversOnTheInterfaceThread() throws Exception {
        //Touching Swing from a background thread is the kind of bug that shows
        //up as a repaint that never happens rather than as an exception.
        List<Boolean> onEventThread = new ArrayList<>();
        RoomPoller poller = new RoomPoller(alwaysReturning(OnlineOutcome.ok(state())));

        poller.start("BCDFGH", "a-token", new RoomPoller.Listener() {
            @Override
            public void updated(RoomState value) {
                onEventThread.add(SwingUtilities.isEventDispatchThread());
            }

            @Override
            public void failed(OnlineOutcome<RoomState> outcome) {
            }
        });
        settle();

        assertEquals(List.of(true), onEventThread);
        poller.stop();
    }

    @Test
    void asksAgainWhileTheGameIsOpen() throws Exception {
        RoomPoller poller = new RoomPoller(alwaysReturning(OnlineOutcome.ok(state())));
        poller.start("BCDFGH", "a-token", listener);

        TimeUnit.MILLISECONDS.sleep(2500);
        settle();

        assertTrue(requests.get() >= 2, "Polled " + requests.get() + " times");
        poller.stop();
    }

    @Test
    void doesNotPileUpRequestsWhenTheServerIsSlow() throws Exception {
        //A slow server must not leave a queue of polls all arriving at once
        //when it recovers.
        CompletableFuture<OnlineOutcome<RoomState>> neverFinishes = new CompletableFuture<>();
        RoomPoller poller = new RoomPoller(clientReturning(neverFinishes));
        poller.start("BCDFGH", "a-token", listener);

        TimeUnit.MILLISECONDS.sleep(2500);

        assertEquals(1, requests.get(), "A second request went out before the first came back");
        poller.stop();
        neverFinishes.complete(OnlineOutcome.ok(state()));
    }

    @Test
    void saysNothingMoreOnceItIsStopped() throws Exception {
        CompletableFuture<OnlineOutcome<RoomState>> pending = new CompletableFuture<>();
        RoomPoller poller = new RoomPoller(clientReturning(pending));
        poller.start("BCDFGH", "a-token", listener);
        TimeUnit.MILLISECONDS.sleep(200);

        poller.stop();
        pending.complete(OnlineOutcome.ok(state()));
        settle();

        assertTrue(seen.isEmpty(),
                "A reply that arrives after stopping is about a game nobody is looking at");
    }

    @Test
    void reportsAFailureRatherThanGoingQuiet() throws Exception {
        RoomPoller poller = new RoomPoller(alwaysReturning(
                OnlineOutcome.failed(OnlineOutcome.Kind.SIGNED_OUT, "Sign in again")));
        poller.start("BCDFGH", "a-token", listener);

        settle();

        assertEquals(1, failures.size());
        assertEquals(OnlineOutcome.Kind.SIGNED_OUT, failures.get(0).kind());
        poller.stop();
    }

    @Test
    void treatsAThrownFailureAsAnUnreachableServer() throws Exception {
        RoomPoller poller = new RoomPoller(clientReturning(
                CompletableFuture.failedFuture(new java.io.IOException("refused"))));
        poller.start("BCDFGH", "a-token", listener);

        settle();

        assertEquals(1, failures.size());
        assertEquals(OnlineOutcome.Kind.UNREACHABLE, failures.get(0).kind());
        poller.stop();
    }

    @Test
    void knowsWhetherItIsRunning() {
        RoomPoller poller = new RoomPoller(alwaysReturning(OnlineOutcome.ok(state())));

        assertFalse(poller.isPolling());
        poller.start("BCDFGH", "a-token", listener);
        assertTrue(poller.isPolling());
        poller.stop();
        assertFalse(poller.isPolling());
    }

    @Test
    void stoppingTwiceIsHarmless() {
        RoomPoller poller = new RoomPoller(alwaysReturning(OnlineOutcome.ok(state())));
        poller.stop();
        poller.stop();
    }

    // --- helpers -------------------------------------------------------

    /** Waits for the poll and the interface-thread delivery that follows it. */
    private void settle() throws Exception {
        TimeUnit.MILLISECONDS.sleep(300);
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static RoomState state() {
        return new RoomState("BCDFGH", RoomStatus.IN_PROGRESS, "host", "guest",
                "Olivia", true, true, true, "host", null, null, List.of(), List.of(), null,
                Instant.now().plusSeconds(600));
    }

    private OnlineGameClient alwaysReturning(OnlineOutcome<RoomState> outcome) {
        return clientReturning(CompletableFuture.completedFuture(outcome));
    }

    private OnlineGameClient clientReturning(
            CompletableFuture<OnlineOutcome<RoomState>> reply) {
        return new OnlineGameClient() {
            @Override
            public CompletableFuture<OnlineOutcome<Room>> createRoom(String token) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<OnlineOutcome<Room>> joinRoom(String code, String token) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<OnlineOutcome<RoomState>> state(
                    String code, String token) {
                requests.incrementAndGet();
                return reply;
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
