package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.ComputerGameStart;
import com.guesswho.game.Game;
import com.guesswho.game.PlayerGameStart;
import com.guesswho.game.QuestionMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class SavedGameTest {
    @Test
    void putsBackTheSetupForAGameAgainstTheComputer() throws Exception {
        Game game = new Game();
        game.startComputerGame("sam", ComputerDifficulty.HARD,
                ComputerGameStart.PLAYER, QuestionMode.PRESET);
        GameSetup setup = new GameSetup();

        saved(game, true).restoreSetup(setup);

        assertTrue(setup.isAgainstComputer());
        assertEquals(ComputerDifficulty.HARD, setup.difficulty());
        assertEquals(QuestionMode.PRESET, setup.questionMode());
        assertEquals("sam", setup.firstUsername());
        assertTrue(setup.tellsCharacterUpFront());
    }

    @Test
    void putsBackTheSetupForTwoPlayers() throws Exception {
        Game game = new Game();
        game.startPlayerGame("sam", 11, "alex", 22,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.FREE_FORM);
        GameSetup setup = new GameSetup();

        saved(game, false).restoreSetup(setup);

        assertTrue(setup.isAgainstPlayer());
        assertEquals("sam", setup.firstUsername());
        assertEquals("alex", setup.secondUsername());
        assertEquals(11, setup.firstBirthday());
        assertEquals(22, setup.secondBirthday());
        assertTrue(setup.isFreeFormQuestions());
    }

    @Test
    void remembersThatThePlayerKeptTheirCharacterToThemselves() throws Exception {
        Game game = new Game();
        game.startComputerGame("sam", ComputerDifficulty.EASY,
                ComputerGameStart.PLAYER, QuestionMode.PRESET);
        GameSetup setup = new GameSetup();

        saved(game, false).restoreSetup(setup);

        assertFalse(setup.tellsCharacterUpFront(),
                "Resuming must not start asking a player to confirm a character they "
                        + "chose not to declare");
    }

    @Test
    void aSaveFromAnotherVersionIsNotReadable() throws Exception {
        Game game = new Game();
        game.startComputerGame("sam", ComputerDifficulty.EASY,
                ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertTrue(saved(game, true).isReadable());
        assertFalse(new SavedGame(SavedGame.VERSION + 1, game.snapshot(), true,
                OpeningTurn.FIRST_PLAYER, List.of(), List.of(), "", "").isReadable());
    }

    private static SavedGame saved(Game game, boolean tellsUpFront) {
        return new SavedGame(SavedGame.VERSION, game.snapshot(), tellsUpFront,
                OpeningTurn.FIRST_PLAYER, List.of(), List.of(), "", "");
    }
}
