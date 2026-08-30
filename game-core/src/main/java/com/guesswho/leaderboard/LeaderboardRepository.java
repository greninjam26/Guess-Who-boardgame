package com.guesswho.leaderboard;

import com.guesswho.game.GameMode;

import java.util.List;

/**
 * Reads aggregated leaderboard standings.
 */
public interface LeaderboardRepository {
    /**
     * Returns standings ordered by wins and then participant name.
     *
     * @param mode game mode to report on, or {@code null} for every mode
     * @param limit maximum number of entries to return
     * @return leaderboard standings
     */
    List<LeaderboardEntry> findStandings(GameMode mode, int limit);
}
