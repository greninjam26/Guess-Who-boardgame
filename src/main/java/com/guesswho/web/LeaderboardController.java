package com.guesswho.web;

import com.guesswho.leaderboard.LeaderboardEntry;
import com.guesswho.leaderboard.LeaderboardRepository;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides leaderboard standings derived from completed games.
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private final LeaderboardRepository leaderboardRepository;

    /**
     * Creates a controller backed by the configured leaderboard repository.
     *
     * @param leaderboardRepository repository used to calculate standings
     */
    public LeaderboardController(LeaderboardRepository leaderboardRepository) {
        this.leaderboardRepository = leaderboardRepository;
    }

    /**
     * Returns current leaderboard standings.
     *
     * @return standings ordered by wins and participant name
     */
    @GetMapping
    public List<LeaderboardEntry> getLeaderboard() {
        return leaderboardRepository.findStandings();
    }
}
