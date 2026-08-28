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
}
