package com.guesswho.persistence;

import java.util.List;

/**
 * Reads completed games from persistent history.
 */
public interface GameResultHistoryRepository {
    /**
     * Returns completed games from newest to oldest.
     *
     * @return immutable completed-game snapshots
     */
    List<StoredGameResult> findAll();
}
