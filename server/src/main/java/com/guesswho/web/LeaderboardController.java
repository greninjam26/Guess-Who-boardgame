package com.guesswho.web;

import com.guesswho.game.GameMode;
import com.guesswho.leaderboard.LeaderboardEntry;
import com.guesswho.leaderboard.LeaderboardRepository;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Provides leaderboard standings derived from completed games.
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

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
     * @param limit maximum number of entries to return
     * @return standings ordered by wins and participant name
     */
    @GetMapping
    public List<LeaderboardEntry> getLeaderboard(
            @RequestParam(required = false) GameMode mode,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and " + MAX_LIMIT);
        }
        return leaderboardRepository.findStandings(mode, limit);
    }
}
