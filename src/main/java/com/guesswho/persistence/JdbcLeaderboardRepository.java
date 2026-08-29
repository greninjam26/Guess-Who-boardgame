package com.guesswho.persistence;

import com.guesswho.leaderboard.LeaderboardEntry;
import com.guesswho.leaderboard.LeaderboardRepository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads leaderboard standings from saved game results.
 */
public class JdbcLeaderboardRepository implements LeaderboardRepository {
    private static final String FIND_STANDINGS_SQL = """
            SELECT
                participant.name,
                COUNT(DISTINCT participant.game_result_id) AS games_played,
                COUNT(DISTINCT CASE
                    WHEN game_result.winner = participant.name
                    THEN participant.game_result_id
                END) AS wins
            FROM game_result_participants participant
            JOIN game_results game_result
              ON game_result.id = participant.game_result_id
            GROUP BY participant.name
            ORDER BY wins DESC, participant.name
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a leaderboard repository using the configured data source.
     *
     * @param jdbcTemplate JDBC operations for the result database
     */
    public JdbcLeaderboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> findStandings() {
        return jdbcTemplate.query(
                FIND_STANDINGS_SQL,
                (resultSet, rowNumber) -> new LeaderboardEntry(
                        resultSet.getString("name"),
                        resultSet.getInt("games_played"),
                        resultSet.getInt("wins")));
    }
}
