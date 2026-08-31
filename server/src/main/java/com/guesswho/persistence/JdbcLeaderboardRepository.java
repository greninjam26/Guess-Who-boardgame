package com.guesswho.persistence;

import com.guesswho.game.GameMode;
import com.guesswho.leaderboard.LeaderboardEntry;
import com.guesswho.leaderboard.LeaderboardRepository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads leaderboard standings from saved game results.
 */
public class JdbcLeaderboardRepository implements LeaderboardRepository {
    //Grouped on the account where there is one and the typed name where there
    //is not. An account's row cannot be joined by somebody typing its name,
    //which is the whole point of signing in; guests keep sharing a row with
    //anyone who typed the same thing, which is what being anonymous costs.
    private static final String STANDINGS_TEMPLATE = """
            SELECT
                COALESCE(account.username, participant.name) AS name,
                CASE WHEN participant.account_id IS NULL THEN FALSE ELSE TRUE END
                    AS registered,
                COUNT(DISTINCT participant.game_result_id) AS games_played,
                COUNT(DISTINCT CASE
                    WHEN game_result.winner = participant.name
                    THEN participant.game_result_id
                END) AS wins
            FROM game_result_participants participant
            JOIN game_results game_result
              ON game_result.id = participant.game_result_id
            LEFT JOIN accounts account
              ON account.id = participant.account_id
            %s
            GROUP BY participant.account_id, COALESCE(account.username, participant.name)
            -- Wins first, then whoever needed fewer games to get them. The
            -- name is only ever the last resort: without it the order of two
            -- identical records is whatever the database felt like, and a
            -- leaderboard that reshuffles between calls can show one player
            -- twice across a page boundary. Sorting on it any earlier means
            -- rewarding a username, which is what this used to do.
            ORDER BY wins DESC, games_played ASC, name
            LIMIT ?
            """;

    private static final String ALL_MODES_SQL = STANDINGS_TEMPLATE.formatted("");
    private static final String SINGLE_MODE_SQL =
            STANDINGS_TEMPLATE.formatted("WHERE game_result.mode = ?");

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
    public List<LeaderboardEntry> findStandings(GameMode mode, int limit) {
        RowMapper<LeaderboardEntry> rowMapper = (resultSet, rowNumber) -> new LeaderboardEntry(
                resultSet.getString("name"),
                resultSet.getInt("games_played"),
                resultSet.getInt("wins"),
                resultSet.getBoolean("registered"));
        if (mode == null) {
            return jdbcTemplate.query(ALL_MODES_SQL, rowMapper, limit);
        }
        return jdbcTemplate.query(SINGLE_MODE_SQL, rowMapper, mode.name(), limit);
    }
}
