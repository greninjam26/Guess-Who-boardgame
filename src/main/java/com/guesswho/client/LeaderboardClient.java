package com.guesswho.client;

import com.guesswho.leaderboard.LeaderboardEntry;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Retrieves leaderboard standings from a remote Guess Who server.
 */
@FunctionalInterface
public interface LeaderboardClient {
    /**
     * Retrieves standings without blocking the calling thread.
     *
     * @return future containing current leaderboard standings
     */
    CompletableFuture<List<LeaderboardEntry>> fetch();
}
