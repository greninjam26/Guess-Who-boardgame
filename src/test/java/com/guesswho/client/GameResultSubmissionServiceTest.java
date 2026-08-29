package com.guesswho.client;

import com.guesswho.game.QuestionMode;
import com.guesswho.game.GameResult;
import com.guesswho.persistence.GameResultRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class GameResultSubmissionServiceTest {
    @Test
    void successfulServerSubmissionDoesNotDuplicateResultLocally() {
        CapturingRepository localFallback = new CapturingRepository();
        GameResultClient serverClient = result -> CompletableFuture.completedFuture(null);
        GameResultSubmissionService submissionService =
                new GameResultSubmissionService(serverClient, localFallback);

        submissionService.submit(gameResult()).join();

        assertTrue(localFallback.savedResults.isEmpty());
    }

    @Test
    void failedServerSubmissionStoresResultLocally() {
        CapturingRepository localFallback = new CapturingRepository();
        GameResultClient serverClient = result -> CompletableFuture.failedFuture(
                new IOException("Server unavailable"));
        GameResultSubmissionService submissionService =
                new GameResultSubmissionService(serverClient, localFallback);
        GameResult gameResult = gameResult();

        submissionService.submit(gameResult).join();

        assertEquals(List.of(gameResult), localFallback.savedResults);
    }

    private GameResult gameResult() {
        return new GameResult(
                List.of(
                        new GameResult.Participant("Player", "Olivia", List.of()),
                        new GameResult.Participant("AI", "Nick", List.of())),
                "Player",
                GameMode.PVE,
                ComputerDifficulty.EASY, QuestionMode.PRESET);
    }

    private static class CapturingRepository implements GameResultRepository {
        private final List<GameResult> savedResults = new ArrayList<>();

        @Override
        public void save(GameResult gameResult) {
            savedResults.add(gameResult);
        }
    }
}
