package com.guesswho.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public GameResultRepository gameResultRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcGameResultRepository(jdbcTemplate);
    }
}
