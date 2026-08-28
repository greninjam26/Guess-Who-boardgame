package com.guesswho.client;

import com.guesswho.game.GameResult;
import com.guesswho.persistence.GameResultRepository;

import java.util.concurrent.CompletableFuture;

/**
 * Submits completed games to the server and preserves them locally when the
 * server cannot accept them.
 */
public class GameResultSubmissionService {
    private final GameResultClient serverClient;
    private final GameResultRepository localFallback;

    /**
     * Creates a server-first result submission service.
     *
     * @param serverClient remote game-result client
     * @param localFallback repository used when the server request fails
     */
    public GameResultSubmissionService(
            GameResultClient serverClient,
            GameResultRepository localFallback) {
        this.serverClient = serverClient;
        this.localFallback = localFallback;
    }

    /**
     * Sends a result to the server, falling back to local persistence if the
     * asynchronous request fails.
     *
     * @param gameResult completed game to submit
     * @return future completed after either destination stores the result
     */
    public CompletableFuture<Void> submit(GameResult gameResult) {
        return serverClient.submit(gameResult)
                .handle((ignored, failure) -> {
                    if (failure != null) {
                        localFallback.save(gameResult);
                    }
                    return null;
                });
    }
}
