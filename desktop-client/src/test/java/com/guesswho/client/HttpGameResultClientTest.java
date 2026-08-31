package com.guesswho.client;

import com.guesswho.game.CharacterCommitment;
import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.QuestionMode;
import com.guesswho.game.GameResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HttpGameResultClientTest {
    @Test
    void postsCompleteGameResultToServerEndpoint() {
        AtomicReference<URI> postedUri = new AtomicReference<>();
        AtomicReference<String> postedBody = new AtomicReference<>();
        HttpGameResultClient client = new HttpGameResultClient(
                URI.create("https://games.example/guess-who"),
                (uri, body, token) -> {
                    postedUri.set(uri);
                    postedBody.set(body);
                    return CompletableFuture.completedFuture(201);
                });

        client.submit(gameResult()).join();

        assertEquals(
                URI.create("https://games.example/api/game-results"),
                postedUri.get());
        String expected = "{\"participants\":["
                + "{\"name\":\"Player \\\"One\\\"\",\"selectedCharacter\":\"Olivia\","
                + "\"questionAnswers\":[{\"question\":\"Glasses?\\nHat?\",\"answer\":true}]},"
                + "{\"name\":\"AI\",\"selectedCharacter\":\"Nick\",\"questionAnswers\":[]}],"
                + "\"winner\":\"Player \\\"One\\\"\","
                + "\"mode\":\"PVE\",\"difficulty\":\"HARD\","
                + "\"questionMode\":\"PRESET\"}";
        assertEquals(expected, postedBody.get());
    }

    @Test
    void rejectsNonCreatedServerResponse() {
        HttpGameResultClient client = new HttpGameResultClient(
                URI.create("http://localhost:8080"),
                (uri, body, token) -> CompletableFuture.completedFuture(500));

        assertThrows(CompletionException.class, () -> client.submit(gameResult()).join());
    }

    @Test
    void sendsTheCommitmentSoAVerifierCanCheckTheReveal() {
        AtomicReference<String> postedBody = new AtomicReference<>();
        CharacterCommitment commitment = CharacterCommitment.to("Olivia");
        HttpGameResultClient client = new HttpGameResultClient(
                URI.create("http://localhost:8080"),
                (uri, body, token) -> {
                    postedBody.set(body);
                    return CompletableFuture.completedFuture(201);
                });

        client.submit(new GameResult(
                List.of(new GameResult.Participant("Alex", "Olivia", List.of(), commitment)),
                "Alex", GameMode.PVP_LOCAL, null, QuestionMode.PRESET)).join();

        assertTrue(postedBody.get().contains("\"hash\":\"" + commitment.hash() + "\""),
                postedBody.get());
        assertTrue(postedBody.get().contains("\"nonce\":\"" + commitment.nonce() + "\""),
                postedBody.get());
    }

    private GameResult gameResult() {
        return new GameResult(
                List.of(
                        new GameResult.Participant(
                                "Player \"One\"",
                                "Olivia",
                                List.of(new GameResult.QuestionAnswer("Glasses?\nHat?", true)), null),
                        new GameResult.Participant("AI", "Nick", List.of(), null)),
                "Player \"One\"",
                GameMode.PVE,
                ComputerDifficulty.HARD, QuestionMode.PRESET);
    }
}
