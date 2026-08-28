package com.guesswho.persistence;

import com.guesswho.game.GameResult;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Appends submitted game results to a configurable CSV file.
 */
@Repository
public class CsvGameResultRepository implements GameResultRepository {
    private final Path resultsFile;

    /**
     * Creates a CSV repository using the configured results-file location.
     *
     * @param resultsFile configured CSV file path
     */
    public CsvGameResultRepository(
            @Value("${guesswho.results.file:test.csv}") String resultsFile) {
        this.resultsFile = Path.of(resultsFile);
    }

    @Override
    public synchronized void save(GameResult gameResult) {
        try {
            StoreResult storeResult = new StoreResult(
                    new FileWriter(resultsFile.toFile(), true));
            storeResult.addGameResult(gameResult);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Unable to store the submitted game result", exception);
        }
    }
}
