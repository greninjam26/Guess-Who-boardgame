package com.guesswho.leaderboard;

/**
 * Aggregated results for one leaderboard participant.
 *
 * @param name participant display name
 * @param gamesPlayed number of distinct games played
 * @param wins number of distinct games won
 */
public record LeaderboardEntry(String name, int gamesPlayed, int wins) {
}
