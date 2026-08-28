package com.guesswho.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the server-side game-result repository.
 */
@Configuration
public class GameResultPersistenceConfiguration {
    /**
     * Creates the CSV repository used by the HTTP server.
     *
     * @param resultsFile configured CSV file path
     * @return configured game-result repository
     */
    @Bean
    public GameResultRepository gameResultRepository(
            @Value("${guesswho.results.file:test.csv}") String resultsFile) {
        return new CsvGameResultRepository(resultsFile);
    }
}
