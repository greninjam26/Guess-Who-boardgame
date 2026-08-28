package com.guesswho.client;

import com.guesswho.game.GameResult;

import java.util.concurrent.CompletableFuture;

/**
 * Sends completed games to a remote Guess Who server.
 */
@FunctionalInterface
public interface GameResultClient {
    /**
     * Submits a completed game without blocking the calling thread.
     *
     * @param gameResult completed game to submit
     * @return future completed when the server accepts the result
     */
    CompletableFuture<Void> submit(GameResult gameResult);
}
