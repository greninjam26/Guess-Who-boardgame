package com.guesswho.client;

import com.guesswho.game.GameResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * File-backed store for completed games awaiting upload.
 *
 * <p>Results are stored one JSON object per line. The format is machine
 * readable on purpose: the queue is drained by reading it back, which the
 * previous human-readable CSV log could not support.</p>
 */
public class FilePendingGameResultStore implements PendingGameResultStore {
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final Path queueFile;

    /**
     * Creates a queue backed by the given file.
     *
     * <p>The caller chooses the location, because a bare file name would be
     * resolved against the working directory, which an installed application
     * does not control. See {@link ApplicationDirectory}.</p>
     *
     * @param queueFile file used to hold results awaiting upload
     */
    public FilePendingGameResultStore(Path queueFile) {
        this.queueFile = queueFile;
    }

    /**
     * Appends one result to the queue.
     *
     * @param gameResult completed game awaiting upload
     */
    @Override
    public synchronized void add(GameResult gameResult) {
        try {
            Files.writeString(
                    queueFile,
                    JSON_MAPPER.writeValueAsString(gameResult) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(
                    "Unable to store the game result for later upload", exception);
        }
    }

    /**
     * Reads every queued result. Lines that cannot be parsed are skipped, since
     * a corrupted entry must not block the rest of the queue.
     *
     * @return queued results in the order they were stored
     */
    @Override
    public synchronized List<GameResult> readAll() {
        if (!Files.exists(queueFile)) {
            return List.of();
        }
        List<GameResult> queued = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(queueFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    queued.add(JSON_MAPPER.readValue(line, GameResult.class));
                }
                catch (JacksonException exception) {
                    continue;
                }
            }
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Unable to read queued game results", exception);
        }
        return List.copyOf(queued);
    }

    /**
     * Replaces the queue contents, removing the file when nothing remains.
     *
     * @param remaining results still awaiting upload
     */
    @Override
    public synchronized void replaceAll(List<GameResult> remaining) {
        try {
            if (remaining.isEmpty()) {
                Files.deleteIfExists(queueFile);
                return;
            }
            StringBuilder contents = new StringBuilder();
            for (GameResult gameResult : remaining) {
                contents.append(JSON_MAPPER.writeValueAsString(gameResult))
                        .append(System.lineSeparator());
            }
            Files.writeString(queueFile, contents.toString(), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Unable to update queued game results", exception);
        }
    }
}
