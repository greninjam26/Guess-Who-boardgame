package com.guesswho.leaderboard;

/**
 * Aggregated results for one leaderboard participant.
 *
 * <p>A row is either an account's or a guest's. Guests are grouped by the name
 * they typed, which means two people who both typed {@code Sam} share a row —
 * unavoidable, and the reason signing in is worth doing. A registered row
 * belongs to one account and nobody else can land in it by typing a name.</p>
 *
 * @param name        the account's username, or the name a guest typed
 * @param gamesPlayed number of distinct games played
 * @param wins        number of distinct games won
 * @param registered  whether this row belongs to an account
 */
public record LeaderboardEntry(String name, int gamesPlayed, int wins, boolean registered) {
}
