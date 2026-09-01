package com.guesswho.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.room.RoomState;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HttpOnlineGameClientTest {
    private static final URI SERVER = URI.create("http://localhost:8080");
    private static final String STATE = """
            {"code":"BCDFGH","status":"IN_PROGRESS","you":"host","opponent":"guest",
             "yourCharacter":"Olivia","opponentHasChosen":true,"yourTurn":true,
             "currentPlayer":"host","questionAwaitingYourAnswer":null,
             "yourUnansweredQuestion":null,"yourQuestions":[],"opponentQuestions":[],
             "winner":null,"expiresAt":"2026-09-01T12:00:00Z"}
            """;

    private final List<HttpOnlineGameClient.Call> sent = new ArrayList<>();

    @Test
    void readsTheGameAsThisPlayerSeesIt() {
        OnlineOutcome<RoomState> outcome =
                clientReturning(200, STATE).state("BCDFGH", "a-token").join();

        assertTrue(outcome.isOk());
        assertEquals("Olivia", outcome.value().yourCharacter());
        assertTrue(outcome.value().opponentHasChosen());
        assertTrue(outcome.value().yourTurn());
    }

    @Test
    void sendsTheSessionTokenWithEveryCall() {
        clientReturning(200, STATE).state("BCDFGH", "a-token").join();

        assertEquals("a-token", sent.get(0).token());
    }

    @Test
    void sendsAKeyWithAMoveButNotWithARead() {
        HttpOnlineGameClient client = clientReturning(200, STATE);

        client.state("BCDFGH", "a-token").join();
        client.ask("BCDFGH", "Does your character wear glasses?", "a-token").join();

        assertNull(sent.get(0).moveKey(), "Reading state is not a move");
        assertNotNull(sent.get(1).moveKey());
    }

    @Test
    void givesEachMoveItsOwnKey() {
        HttpOnlineGameClient client = clientReturning(200, STATE);

        client.ask("BCDFGH", "Does your character wear glasses?", "a-token").join();
        client.ask("BCDFGH", "Is the person wearing a hat?", "a-token").join();

        assertNotEquals(sent.get(0).moveKey(), sent.get(1).moveKey(),
                "Two different moves sharing a key means the second never happens");
    }

    @Test
    void retriesAFailedMoveWithTheSameKey() {
        //The whole point of the keys: the client can retry without knowing
        //whether the first attempt reached the server.
        AtomicInteger attempts = new AtomicInteger();
        HttpOnlineGameClient client = new HttpOnlineGameClient(SERVER, call -> {
            sent.add(call);
            if (attempts.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new IOException("connection reset"));
            }
            return CompletableFuture.completedFuture(
                    new HttpOnlineGameClient.Response(200, STATE));
        });

        assertTrue(client.ask("BCDFGH", "Does your character wear glasses?", "a-token")
                .join().isOk());

        assertEquals(2, sent.size());
        assertEquals(sent.get(0).moveKey(), sent.get(1).moveKey(),
                "A retry with a fresh key would be treated as a second move");
    }

    @Test
    void givesUpAfterOneRetryRatherThanHangingOnForEver() {
        HttpOnlineGameClient client = failingClient();

        OnlineOutcome<RoomState> outcome =
                client.ask("BCDFGH", "Does your character wear glasses?", "a-token")
                        .handle((value, failure) -> failure == null ? value
                                : OnlineOutcome.<RoomState>failed(
                                        OnlineOutcome.Kind.UNREACHABLE, "unreachable"))
                        .join();

        assertFalse(outcome.isOk());
        assertEquals(2, sent.size(), "One attempt and one retry, then tell the player");
    }

    @Test
    void tellsAPlayerWhenTheRulesRefusedTheirMove() {
        OnlineOutcome<RoomState> outcome = clientReturning(409,
                "{\"detail\":\"It is not your turn\"}")
                .ask("BCDFGH", "Does your character wear glasses?", "a-token").join();

        assertEquals(OnlineOutcome.Kind.REFUSED, outcome.kind());
        assertEquals("It is not your turn", outcome.message());
    }

    @Test
    void tellsAPlayerWhenTheirSessionHasGone() {
        //Different from a refusal: this one needs them to sign in again rather
        //than to do something different in the game.
        OnlineOutcome<RoomState> outcome =
                clientReturning(401, "").state("BCDFGH", "a-stale-token").join();

        assertEquals(OnlineOutcome.Kind.SIGNED_OUT, outcome.kind());
        assertTrue(outcome.message().contains("Sign in"));
    }

    @Test
    void tellsAPlayerWhenACodeOpensNothing() {
        assertEquals(OnlineOutcome.Kind.NOT_FOUND,
                clientReturning(404, "").joinRoom("BCDFGH", "a-token").join().kind());
    }

    @Test
    void tellsAPlayerWhenTheyHaveTooManyGamesOpen() {
        OnlineOutcome<?> outcome = clientReturning(429,
                "{\"detail\":\"You already have 5 games open. Finish one first.\"}")
                .createRoom("a-token").join();

        assertEquals(OnlineOutcome.Kind.TOO_MANY_ROOMS, outcome.kind());
        assertTrue(outcome.message().contains("Finish one first"));
    }

    @Test
    void stillSaysSomethingWhenTheServerExplainsNothing() {
        OnlineOutcome<RoomState> outcome = clientReturning(409, "not json")
                .ask("BCDFGH", "Does your character wear glasses?", "a-token").join();

        assertEquals(OnlineOutcome.Kind.REFUSED, outcome.kind());
        assertFalse(outcome.message() == null || outcome.message().isBlank());
    }

    @Test
    void escapesAQuestionThatWouldBreakTheJson() {
        //Free-form questions are typed by a player, and a quotation mark in one
        //would otherwise produce a body the server cannot read.
        clientReturning(200, STATE)
                .ask("BCDFGH", "Is it \"Sam\"?", "a-token").join();

        assertTrue(sent.get(0).body().contains("\\\"Sam\\\""), sent.get(0).body());
    }

    @Test
    void readsAStateFromAServerWithFieldsThisClientDoesNotKnow() {
        //A server ahead of an installed client should not take the game down.
        OnlineOutcome<RoomState> outcome = clientReturning(200,
                "{\"code\":\"BCDFGH\",\"status\":\"WAITING\",\"you\":\"host\","
                        + "\"somethingNew\":42}")
                .state("BCDFGH", "a-token").join();

        assertTrue(outcome.isOk());
        assertEquals("host", outcome.value().you());
    }

    // --- helpers -------------------------------------------------------

    private HttpOnlineGameClient clientReturning(int status, String body) {
        return new HttpOnlineGameClient(SERVER, call -> {
            sent.add(call);
            return CompletableFuture.completedFuture(
                    new HttpOnlineGameClient.Response(status, body));
        });
    }

    private HttpOnlineGameClient failingClient() {
        return new HttpOnlineGameClient(SERVER, call -> {
            sent.add(call);
            return CompletableFuture.failedFuture(new IOException("connection refused"));
        });
    }

    private static void assertNotNull(Object value) {
        assertTrue(value != null, "expected a value");
    }
}
