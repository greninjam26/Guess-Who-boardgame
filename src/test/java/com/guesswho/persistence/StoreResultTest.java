package com.guesswho.persistence;

import com.guesswho.game.GameResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoreResultTest {
    @Test
    void storesReadableComputerGameValues() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        GameResult result = new GameResult(
                List.of(
                        new GameResult.Participant(
                                "Player",
                                "Olivia",
                                List.of(new GameResult.QuestionAnswer(
                                        "Is your character's eye colour brown?", false))),
                        new GameResult.Participant(
                                "AI",
                                "Nick",
                                List.of(new GameResult.QuestionAnswer(
                                        "Is your character's eye colour blue?", true)))),
                "Player",
                GameMode.PVE,
                ComputerDifficulty.EASY);

        storeResult.addGameResult(result);

        String expected = String.join(System.lineSeparator(),
                "Player,Olivia,Is your character's eye colour brown?, no",
                "AI,Nick,Is your character's eye colour blue?, yes",
                "Player,PVE,EASY",
                "");
        assertEquals(expected, output.toString());
    }

    @Test
    void storesBothPlayersQuestionsAndWinnerOnSeparateRows() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        GameResult result = new GameResult(
                List.of(
                        new GameResult.Participant(
                                "Player 1",
                                "Olivia",
                                List.of(
                                        new GameResult.QuestionAnswer(
                                                "Is your character's eye colour brown?", false),
                                        new GameResult.QuestionAnswer(
                                                "Does your character look friendly?", true))),
                        new GameResult.Participant(
                                "Player 2",
                                "Nick",
                                List.of(new GameResult.QuestionAnswer(
                                        "Is your character's eye colour blue?", true)))),
                "Player 2",
                GameMode.PVP_LOCAL,
                null);

        storeResult.addGameResult(result);

        String expected = String.join(System.lineSeparator(),
                "Player 1,Olivia,Is your character's eye colour brown?, no,"
                        + "Does your character look friendly?, yes",
                "Player 2,Nick,Is your character's eye colour blue?, yes",
                "Player 2,PVP_LOCAL",
                "");
        assertEquals(expected, output.toString());
    }

    @Test
    void keepsRowsSeparateWhenNoQuestionsWereAsked() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        GameResult result = new GameResult(
                List.of(
                        new GameResult.Participant("Player", "Olivia", List.of()),
                        new GameResult.Participant("AI", "Nick", List.of())),
                "AI",
                GameMode.PVE,
                ComputerDifficulty.EASY);

        storeResult.addGameResult(result);

        String expected = String.join(System.lineSeparator(),
                "Player,Olivia",
                "AI,Nick",
                "AI,PVE,EASY",
                "");
        assertEquals(expected, output.toString());
    }

    @Test
    void escapesCsvFieldsContainingCommasAndQuotes() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        GameResult result = new GameResult(
                List.of(
                        new GameResult.Participant("Doe, \"Jane\"", "Olivia", List.of()),
                        new GameResult.Participant("Opponent", "Nick", List.of())),
                "Doe, \"Jane\"",
                GameMode.PVP_LOCAL,
                null);

        storeResult.addGameResult(result);

        String expected = String.join(System.lineSeparator(),
                "\"Doe, \"\"Jane\"\"\",Olivia",
                "Opponent,Nick",
                "\"Doe, \"\"Jane\"\"\",PVP_LOCAL",
                "");
        assertEquals(expected, output.toString());
    }
}
