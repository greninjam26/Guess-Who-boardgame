package com.guesswho.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.client.AccountClient.Outcome;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class HttpAccountClientTest {
    private static final URI SERVER = URI.create("http://localhost:8080");

    private final List<HttpAccountClient.Call> sent = new ArrayList<>();

    @Test
    void logsInAndKeepsTheToken() {
        HttpAccountClient client = clientReturning(201,
                "{\"token\":\"a-token\",\"expiresAt\":\"2026-09-30T00:00:00Z\","
                        + "\"account\":{\"id\":7,\"username\":\"greninja\"}}");

        Outcome outcome = client.logIn("greninja", "a-good-password").join();

        assertTrue(outcome.isLoggedIn());
        assertEquals("a-token", outcome.token());
        assertEquals("greninja", outcome.account().username());
        assertEquals(7, outcome.account().id());
    }

    @Test
    void tellsTheDifferenceBetweenAWrongPasswordAndAnUnreachableServer() {
        //These are two different sentences on a login screen: one is the
        //player's mistake, the other is not their fault at all.
        assertEquals(Outcome.Kind.WRONG_CREDENTIALS,
                clientReturning(401, "").logIn("greninja", "wrong").join().kind());
        assertEquals(Outcome.Kind.UNREACHABLE,
                failingClient().logIn("greninja", "a-good-password").join().kind());
    }

    @Test
    void reportsAServerThatCannotBeReachedAsSomethingSurvivable() {
        Outcome outcome = failingClient().logIn("greninja", "a-good-password").join();

        assertTrue(outcome.message().contains("offline"),
                "Somebody who cannot reach the server can still play the game");
    }

    @Test
    void reportsANameSomebodyElseHas() {
        Outcome outcome = clientReturning(409, "").register("greninja", "pw").join();

        assertEquals(Outcome.Kind.USERNAME_TAKEN, outcome.kind());
        assertNull(outcome.token());
    }

    @Test
    void passesOnWhyTheServerRefusedARegistration() {
        Outcome outcome = clientReturning(400,
                "{\"detail\":\"Username must be between 3 and 32 characters\"}")
                .register("ab", "a-good-password").join();

        assertEquals(Outcome.Kind.REJECTED, outcome.kind());
        assertEquals("Username must be between 3 and 32 characters", outcome.message());
    }

    @Test
    void stillSaysSomethingWhenTheServerExplainsNothing() {
        Outcome outcome = clientReturning(400, "not json at all")
                .register("ab", "a-good-password").join();

        assertEquals(Outcome.Kind.REJECTED, outcome.kind());
        assertTrue(outcome.message() != null && !outcome.message().isBlank());
    }

    @Test
    void sendsTheTokenAsABearerHeader() {
        clientReturning(200, "{\"id\":7,\"username\":\"greninja\"}").whoAmI("a-token").join();

        assertEquals("a-token", sent.get(0).token());
        assertEquals("GET", sent.get(0).method());
        assertTrue(sent.get(0).endpoint().toString().endsWith("/api/sessions/current"));
    }

    @Test
    void treatsARejectedTokenAsNotLoggedIn() {
        assertTrue(clientReturning(401, "").whoAmI("a-stale-token").join().isEmpty());
    }

    @Test
    void treatsAnUnreachableServerAsNotLoggedIn() {
        //Rather than an error: a game that will not start because the
        //leaderboard server is down would be a worse bug than a lost login.
        assertTrue(failingClient().whoAmI("a-token").join().isEmpty());
    }

    @Test
    void logsOutEvenWhenTheServerDoesNotAnswer() {
        //The game has already forgotten the token; there is nothing useful to
        //tell the player about the server not hearing so.
        assertNull(failingClient().logOut("a-token").join());
    }

    @Test
    void sendsTheCredentialsAsJson() {
        clientReturning(201, "{\"id\":1,\"username\":\"greninja\"}")
                .register("greninja", "a-good-password").join();

        assertTrue(sent.get(0).body().contains("greninja"));
        assertTrue(sent.get(0).body().contains("a-good-password"));
        assertEquals("POST", sent.get(0).method());
    }

    // --- helpers -------------------------------------------------------

    private HttpAccountClient clientReturning(int status, String body) {
        return new HttpAccountClient(SERVER, call -> {
            sent.add(call);
            return CompletableFuture.completedFuture(
                    new HttpAccountClient.Response(status, body));
        });
    }

    private HttpAccountClient failingClient() {
        return new HttpAccountClient(SERVER, call -> {
            sent.add(call);
            return CompletableFuture.failedFuture(new IOException("connection refused"));
        });
    }
}
