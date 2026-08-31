package com.guesswho.client;

import com.guesswho.game.GameMode;
import com.guesswho.leaderboard.LeaderboardEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpLeaderboardClientTest {
    @Test
    void getsAndParsesLeaderboardFromServerEndpoint() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        HttpLeaderboardClient client = new HttpLeaderboardClient(
                URI.create("https://games.example/guess-who"),
                uri -> {
                    requestedUri.set(uri);
                    return CompletableFuture.completedFuture(new HttpLeaderboardClient.Response(
                            200,
                            """
                            [
                              {"name":"Alex","gamesPlayed":3,"wins":2},
                              {"name":"AI","gamesPlayed":3,"wins":1}
                            ]
                            """));
                });

        assertEquals(
                List.of(
                        new LeaderboardEntry("Alex", 3, 2, false),
                        new LeaderboardEntry("AI", 3, 1, false)),
                client.fetch(null).join());
        assertEquals(
                URI.create("https://games.example/api/leaderboard"),
                requestedUri.get());
    }

    @Test
    void requestsASingleModeWhenOneIsGiven() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        HttpLeaderboardClient client = new HttpLeaderboardClient(
                URI.create("https://games.example/guess-who"),
                uri -> {
                    requestedUri.set(uri);
                    return CompletableFuture.completedFuture(
                            new HttpLeaderboardClient.Response(200, "[]"));
                });

        client.fetch(GameMode.PVE).join();

        assertEquals(
                URI.create("https://games.example/api/leaderboard?mode=PVE"),
                requestedUri.get());
    }

    @Test
    void rejectsNonSuccessfulServerResponse() {
        HttpLeaderboardClient client = new HttpLeaderboardClient(
                URI.create("http://localhost:8080"),
                uri -> CompletableFuture.completedFuture(
                        new HttpLeaderboardClient.Response(503, "[]")));

        assertThrows(CompletionException.class, () -> client.fetch(null).join());
    }

    @Test
    void rejectsMalformedLeaderboardResponse() {
        HttpLeaderboardClient client = new HttpLeaderboardClient(
                URI.create("http://localhost:8080"),
                uri -> CompletableFuture.completedFuture(
                        new HttpLeaderboardClient.Response(200, "not-json")));

        assertThrows(CompletionException.class, () -> client.fetch(null).join());
    }

    @Test
    void propagatesNetworkFailure() {
        HttpLeaderboardClient client = new HttpLeaderboardClient(
                URI.create("http://localhost:8080"),
                uri -> CompletableFuture.failedFuture(
                        new IllegalStateException("Server unavailable")));

        assertThrows(CompletionException.class, () -> client.fetch(null).join());
    }

    @Test
    void readsARowThatSaysWhetherItBelongsToAnAccount() {
        LeaderboardEntry entry = returning(
                "[{\"name\":\"greninja\",\"gamesPlayed\":3,\"wins\":2,\"registered\":true}]")
                .fetch(null).join().get(0);

        assertTrue(entry.registered());
        assertEquals("greninja", entry.name());
    }

    @Test
    void survivesAResponseWithoutTheRegisteredField() {
        //An older server does not send it. Failing to parse the whole list over
        //a missing flag would take the leaderboard down entirely.
        LeaderboardEntry entry = returning(
                "[{\"name\":\"Sam\",\"gamesPlayed\":3,\"wins\":2}]")
                .fetch(null).join().get(0);

        assertFalse(entry.registered());
        assertEquals(3, entry.gamesPlayed());
    }

    private static HttpLeaderboardClient returning(String body) {
        return new HttpLeaderboardClient(
                URI.create("https://games.example"),
                endpoint -> java.util.concurrent.CompletableFuture.completedFuture(
                        new HttpLeaderboardClient.Response(200, body)));
    }
}
