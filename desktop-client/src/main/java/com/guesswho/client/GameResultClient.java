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
    default CompletableFuture<Void> submit(GameResult gameResult) {
        return submit(gameResult, null);
    }

    /**
     * Submits a completed game as a signed-in player.
     *
     * <p>The token is what tells the server whose game this was. Without one
     * the result is still stored, as a guest's — playing without an account is
     * supported, and a game nobody can be attributed to is still a game.</p>
     *
     * @param gameResult completed game to submit
     * @param token      the session token, or null when playing as a guest
     * @return completes when the server has stored it
     */
    CompletableFuture<Void> submit(GameResult gameResult, String token);
}
