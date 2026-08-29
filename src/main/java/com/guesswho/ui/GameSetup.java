package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.QuestionMode;

/**
 * Choices collected by the setup screens before a game starts.
 *
 * <p>Replaces the free-text {@code modeChoice} flag the interface used to
 * branch on. The mode-matrix rule is enforced here rather than restated at
 * every call site: a game against the computer is always played with preset
 * questions, because {@code ComputerPlayer} can only answer questions that
 * exist on the board.</p>
 */
class GameSetup {
    private GameMode mode;
    private ComputerDifficulty difficulty;
    private QuestionMode questionMode;
    private String firstUsername;
    private int firstBirthday;
    private String secondUsername;
    private int secondBirthday;

    /**
     * Configures a game against the computer at the given difficulty.
     *
     * @param computerDifficulty difficulty for the computer opponent
     */
    void againstComputer(ComputerDifficulty computerDifficulty) {
        mode = GameMode.PVE;
        difficulty = computerDifficulty;
        questionMode = QuestionMode.PRESET;
    }

    /**
     * Configures a game between two people sharing this machine.
     *
     * @param questions how questions are chosen during the game
     */
    void againstPlayer(QuestionMode questions) {
        mode = GameMode.PVP_LOCAL;
        difficulty = null;
        questionMode = questions;
    }

    /**
     * Reports whether the opponent is the computer.
     *
     * @return {@code true} for a player-versus-computer game
     */
    boolean isAgainstComputer() {
        return mode == GameMode.PVE;
    }

    /**
     * Reports whether the opponent is another person on this machine.
     *
     * @return {@code true} for a player-versus-player game
     */
    boolean isAgainstPlayer() {
        return mode == GameMode.PVP_LOCAL;
    }

    /**
     * Reports whether players type their own questions.
     *
     * @return {@code true} when questions are free-form
     */
    boolean isFreeFormQuestions() {
        return questionMode == QuestionMode.FREE_FORM;
    }

    /**
     * Returns how questions are chosen.
     *
     * @return the configured question mode
     */
    QuestionMode questionMode() {
        return questionMode;
    }

    /**
     * Returns the computer difficulty.
     *
     * @return the difficulty, or {@code null} in a two-player game
     */
    ComputerDifficulty difficulty() {
        return difficulty;
    }

    String firstUsername() {
        return firstUsername;
    }

    void firstUsername(String username) {
        firstUsername = username;
    }

    int firstBirthday() {
        return firstBirthday;
    }

    void firstBirthday(int birthday) {
        firstBirthday = birthday;
    }

    String secondUsername() {
        return secondUsername;
    }

    void secondUsername(String username) {
        secondUsername = username;
    }

    int secondBirthday() {
        return secondBirthday;
    }

    void secondBirthday(int birthday) {
        secondBirthday = birthday;
    }
}
