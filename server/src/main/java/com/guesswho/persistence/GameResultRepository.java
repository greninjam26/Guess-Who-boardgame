package com.guesswho.persistence;

import com.guesswho.game.GameResult;

/**
 * Stores completed game snapshots independently of the transport that
 * submitted them.
 */
public interface GameResultRepository {
    /**
     * Persists a completed game result.
     *
     * @param gameResult completed-game snapshot to persist
     */
    void save(GameResult gameResult);

    /**
     * Stores a completed game, attributing the first participant to an account.
     *
     * <p>The account comes from whoever was signed in on the client that sent
     * the result, never from the request body: a body that could name an
     * account is a body that could claim somebody else's.</p>
     *
     * <p>The first participant is the one attributed because the client sending
     * a result is the machine its first player is sitting at. A second player
     * sharing the keyboard stays a name, as does everyone playing as a guest.</p>
     *
     * @param gameResult the completed game
     * @param accountId  the signed-in account, or null when a guest submitted it
     */
    void save(GameResult gameResult, Long accountId);
}
