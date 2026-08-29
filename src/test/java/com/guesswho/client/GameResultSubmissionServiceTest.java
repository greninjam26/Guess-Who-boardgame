package com.guesswho.client;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.game.QuestionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class GameResultSubmissionServiceTest {
    @Test
    void successfulServerSubmissionDoesNotQueueResultLocally() {
        CapturingStore pending = new CapturingStore();
        GameResultClient serverClient = result -> CompletableFuture.completedFuture(null);
        GameResultSubmissionService submissionService =
                new GameResultSubmissionService(serverClient, pending);

        submissionService.submit(gameResult("Player")).join();

        assertTrue(pending.stored.isEmpty());
    }

    @Test
    void failedServerSubmissionQueuesResultLocally() {
        CapturingStore pending = new CapturingStore();
        GameResultClient serverClient = result -> CompletableFuture.failedFuture(
                new IOException("Server unavailable"));
        GameResultSubmissionService submissionService =
                new GameResultSubmissionService(serverClient, pending);
        GameResult gameResult = gameResult("Player");

        submissionService.submit(gameResult).join();

        assertEquals(List.of(gameResult), pending.stored);
    }

    @Test
    void uploadsQueuedResultsAfterTheServerBecomesReachable() {
        CapturingStore pending = new CapturingStore();
        GameResult queued = gameResult("Earlier");
        pending.add(queued);
        List<GameResult> submitted = new ArrayList<>();
        GameResultClient serverClient = result -> {
            submitted.add(result);
            return CompletableFuture.completedFuture(null);
        };
        GameResultSubmissionService submissionService =
                new GameResultSubmissionService(serverClient, pending);
        GameResult current = gameResult("Player");

        submissionService.submit(current).join();

        assertEquals(List.of(current, queued), submitted);
        assertTrue(pending.stored.isEmpty(), "Uploaded results must leave the queue");
    }

    @Test
    void keepsQueuedResultsThatStillCannotBeUploaded() {
        CapturingStore pending = new CapturingStore();
        GameResult queued = gameResult("Earlier");
        pending.add(queued);
        GameResultClient serverClient = result -> result.winner().equals("Player")
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new IOException("Rejected"));
        GameResultSubmissionService submissionService =
                new GameResultSubmissionService(serverClient, pending);

        submissionService.submit(gameResult("Player")).join();

        assertEquals(List.of(queued), pending.stored,
                "A result the server still rejects must stay queued");
    }

    private GameResult gameResult(String winner) {
        return new GameResult(
                List.of(
                        new GameResult.Participant(winner, "Olivia", List.of()),
                        new GameResult.Participant("AI", "Nick", List.of())),
                winner,
                GameMode.PVE,
                ComputerDifficulty.EASY,
                QuestionMode.PRESET);
    }

    private static class CapturingStore implements PendingGameResultStore {
        private final List<GameResult> stored = new ArrayList<>();

        @Override
        public void add(GameResult gameResult) {
            stored.add(gameResult);
        }

        @Override
        public List<GameResult> readAll() {
            return List.copyOf(stored);
        }

        @Override
        public void replaceAll(List<GameResult> remaining) {
            stored.clear();
            stored.addAll(remaining);
        }
    }
}
