package com.guesswho.leaderboard;

import java.util.List;

/**
 * Reads aggregated leaderboard standings.
 */
public interface LeaderboardRepository {
    /**
     * Returns standings ordered by wins and then participant name.
     *
     * @return leaderboard standings
     */
    List<LeaderboardEntry> findStandings();
}
