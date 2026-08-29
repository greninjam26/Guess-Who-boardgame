package com.guesswho.client;

import com.guesswho.game.GameResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Submits completed games to the server, queueing them locally when the server
 * cannot accept them and uploading the queue on the next successful submission.
 */
public class GameResultSubmissionService {
    private final GameResultClient serverClient;
    private final PendingGameResultStore pendingResults;

    /**
     * Creates a server-first result submission service.
     *
     * @param serverClient remote game-result client
     * @param pendingResults queue holding results awaiting upload
     */
    public GameResultSubmissionService(
            GameResultClient serverClient,
            PendingGameResultStore pendingResults) {
        this.serverClient = serverClient;
        this.pendingResults = pendingResults;
    }

    /**
     * Sends a result to the server. A failed submission is queued for later. A
     * successful one is taken as proof the server is reachable, so anything
     * already queued is uploaded straight afterwards.
     *
     * @param gameResult completed game to submit
     * @return future completed once the result is stored or queued
     */
    public CompletableFuture<Void> submit(GameResult gameResult) {
        return serverClient.submit(gameResult)
                .handle((ignored, failure) -> failure == null)
                .thenCompose(accepted -> {
                    if (!accepted) {
                        pendingResults.add(gameResult);
                        return CompletableFuture.completedFuture(null);
                    }
                    return uploadQueued();
                });
    }

    private CompletableFuture<Void> uploadQueued() {
        List<GameResult> queued = pendingResults.readAll();
        if (queued.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<List<GameResult>> uploads =
                CompletableFuture.completedFuture(new ArrayList<>());
        for (GameResult queuedResult : queued) {
            uploads = uploads.thenCompose(stillPending -> serverClient.submit(queuedResult)
                    .handle((ignored, failure) -> {
                        if (failure != null) {
                            stillPending.add(queuedResult);
                        }
                        return stillPending;
                    }));
        }
        return uploads.thenAccept(pendingResults::replaceAll);
    }
}
