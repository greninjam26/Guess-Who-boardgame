package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.QuestionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GameSetupTest {
    @Test
    void aComputerGameAlwaysUsesPresetQuestions() {
        GameSetup setup = new GameSetup();

        setup.againstComputer(ComputerDifficulty.HARD);

        assertFalse(setup.isFreeFormQuestions(),
                "ComputerPlayer can only answer questions that exist on the board");
        assertEquals(QuestionMode.PRESET, setup.questionMode());
    }

    @Test
    void aComputerGameRecordsItsDifficulty() {
        GameSetup setup = new GameSetup();

        setup.againstComputer(ComputerDifficulty.EASY);

        assertTrue(setup.isAgainstComputer());
        assertFalse(setup.isAgainstPlayer());
        assertEquals(ComputerDifficulty.EASY, setup.difficulty());
    }

    @Test
    void aTwoPlayerGameCarriesNoDifficulty() {
        GameSetup setup = new GameSetup();

        setup.againstPlayer(QuestionMode.FREE_FORM);

        assertTrue(setup.isAgainstPlayer());
        assertFalse(setup.isAgainstComputer());
        assertNull(setup.difficulty());
        assertTrue(setup.isFreeFormQuestions());
    }

    @Test
    void switchingFromAComputerGameClearsTheDifficulty() {
        GameSetup setup = new GameSetup();
        setup.againstComputer(ComputerDifficulty.HARD);

        setup.againstPlayer(QuestionMode.PRESET);

        assertNull(setup.difficulty(),
                "A difficulty left over from an earlier choice would be recorded with the result");
    }

    @Test
    void holdsBothPlayersDetails() {
        GameSetup setup = new GameSetup();

        setup.firstUsername("Alex");
        setup.firstBirthday(20000101);
        setup.secondUsername("Blake");
        setup.secondBirthday(20010101);

        assertEquals("Alex", setup.firstUsername());
        assertEquals(20000101, setup.firstBirthday());
        assertEquals("Blake", setup.secondUsername());
        assertEquals(20010101, setup.secondBirthday());
    }
}
