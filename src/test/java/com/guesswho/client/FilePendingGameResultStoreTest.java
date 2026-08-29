package com.guesswho.client;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.game.QuestionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePendingGameResultStoreTest {
    @TempDir
    private Path directory;

    @Test
    void readsBackEveryStoredResult() {
        FilePendingGameResultStore store = store();
        GameResult first = gameResult("Alex");
        GameResult second = gameResult("Blake");

        store.add(first);
        store.add(second);

        assertEquals(List.of(first, second), store.readAll());
    }

    @Test
    void preservesQuestionHistoriesThroughARoundTrip() {
        FilePendingGameResultStore store = store();
        GameResult withHistory = new GameResult(
                List.of(new GameResult.Participant(
                        "Alex",
                        "Olivia",
                        List.of(
                                new GameResult.QuestionAnswer("Glasses?", true),
                                new GameResult.QuestionAnswer("Hat?", false)))),
                "Alex",
                GameMode.PVP_LOCAL,
                null,
                QuestionMode.FREE_FORM);

        store.add(withHistory);

        assertEquals(List.of(withHistory), store.readAll());
    }

    @Test
    void reportsNothingWhenNoResultsHaveBeenStored() {
        assertTrue(store().readAll().isEmpty());
    }

    @Test
    void removesTheFileOnceEverythingHasBeenUploaded() throws IOException {
        FilePendingGameResultStore store = store();
        store.add(gameResult("Alex"));

        store.replaceAll(List.of());

        assertTrue(store.readAll().isEmpty());
        assertFalse(Files.exists(queueFile()));
    }

    @Test
    void keepsOnlyTheResultsStillAwaitingUpload() {
        FilePendingGameResultStore store = store();
        GameResult uploaded = gameResult("Alex");
        GameResult stillPending = gameResult("Blake");
        store.add(uploaded);
        store.add(stillPending);

        store.replaceAll(List.of(stillPending));

        assertEquals(List.of(stillPending), store.readAll());
    }

    @Test
    void skipsCorruptedLinesRatherThanBlockingTheQueue() throws IOException {
        FilePendingGameResultStore store = store();
        GameResult readable = gameResult("Alex");
        store.add(readable);
        Files.writeString(
                queueFile(),
                "{not valid json" + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);

        assertEquals(List.of(readable), store.readAll());
    }

    private FilePendingGameResultStore store() {
        return new FilePendingGameResultStore(queueFile().toString());
    }

    private Path queueFile() {
        return directory.resolve("pending-game-results.jsonl");
    }

    private GameResult gameResult(String winner) {
        return new GameResult(
                List.of(
                        new GameResult.Participant(winner, "Olivia", List.of()),
                        new GameResult.Participant("AI", "Nick", List.of())),
                winner,
                GameMode.PVE,
                ComputerDifficulty.EASY,
                QuestionMode.PRESET);
    }
}
