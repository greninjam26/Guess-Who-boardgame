package com.guesswho.client;

import com.guesswho.game.GameMode;
import com.guesswho.leaderboard.LeaderboardEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                        new LeaderboardEntry("Alex", 3, 2),
                        new LeaderboardEntry("AI", 3, 1)),
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
}
