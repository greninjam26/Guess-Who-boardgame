package com.guesswho.persistence;

import java.util.List;

/**
 * Reads completed games from persistent history.
 */
public interface GameResultHistoryRepository {
    /**
     * Returns one page of completed games, newest first.
     *
     * <p>There is deliberately no unbounded read. Every game carries its
     * participants and their full question histories, so an unbounded query
     * grows without limit as games accumulate.</p>
     *
     * @param limit maximum number of games to return
     * @param offset number of games to skip
     * @return immutable completed-game snapshots
     */
    List<StoredGameResult> findPage(int limit, int offset);
}
