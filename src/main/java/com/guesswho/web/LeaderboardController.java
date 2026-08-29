package com.guesswho.web;

import com.guesswho.game.GameMode;
import com.guesswho.leaderboard.LeaderboardEntry;
import com.guesswho.leaderboard.LeaderboardRepository;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * @param mode game mode to report on, or {@code null} for every mode
     * @return standings ordered by wins and participant name
     */
    @GetMapping
    public List<LeaderboardEntry> getLeaderboard(
            @RequestParam(required = false) GameMode mode) {
        return leaderboardRepository.findStandings(mode);
    }
}
