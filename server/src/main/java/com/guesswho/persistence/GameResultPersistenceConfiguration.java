package com.guesswho.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guesswho.leaderboard.LeaderboardRepository;

/**
 * Configures the server-side game-result repository.
 */
@Configuration
public class GameResultPersistenceConfiguration {
    /**
     * Creates the JDBC repository used by the HTTP server.
     *
     * @param jdbcTemplate configured database operations
     * @return configured game-result repository
     */
    @Bean
    public JdbcGameResultRepository gameResultRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcGameResultRepository(jdbcTemplate);
    }

    /**
     * Creates the JDBC repository used to calculate leaderboard standings.
     *
     * @param jdbcTemplate configured database operations
     * @return configured leaderboard repository
     */
    @Bean
    public LeaderboardRepository leaderboardRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcLeaderboardRepository(jdbcTemplate);
    }

    /**
     * Creates the repository holding registered players.
     *
     * @param jdbcTemplate configured database operations
     * @return configured account repository
     */
    @Bean
    public AccountRepository accountRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcAccountRepository(jdbcTemplate);
    }

    /**
     * Creates the repository holding logged-in sessions.
     *
     * @param jdbcTemplate configured database operations
     * @return configured session repository
     */
    @Bean
    public SessionRepository sessionRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcSessionRepository(jdbcTemplate);
    }
}
