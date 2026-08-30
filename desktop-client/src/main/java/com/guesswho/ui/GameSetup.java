package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.QuestionMode;

/**
 * Choices collected by the setup screens before a game starts.
 *
 * <p>Replaces the free-text {@code modeChoice} flag the interface used to
 * branch on, so a mode is a pair of typed choices rather than a sentence to be
 * parsed.</p>
 */
class GameSetup {
    private GameMode mode;
    private ComputerDifficulty difficulty;
    private QuestionMode questionMode;
    private boolean tellsCharacterUpFront = true;
    private String firstUsername;
    private int firstBirthday;
    private String secondUsername;
    private int secondBirthday;

    /**
     * Configures a game against the computer.
     *
     * @param computerDifficulty difficulty for the computer opponent
     * @param questions how questions are chosen during the game
     */
    void againstComputer(ComputerDifficulty computerDifficulty, QuestionMode questions) {
        mode = GameMode.PVE;
        difficulty = computerDifficulty;
        questionMode = questions;
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
     * Reports whether the player tells the game their character before playing.
     *
     * <p>Telling it up front is what makes the answer review conclusive: the
     * character is fixed before a single question is asked. Waiting until the
     * end keeps the feel of the physical game, where the card stays in your own
     * tray — but then the review can only catch a careless answer, not a
     * character picked afterwards to fit the answers given.</p>
     *
     * @return {@code true} when the character is chosen before play
     */
    boolean tellsCharacterUpFront() {
        return tellsCharacterUpFront;
    }

    /**
     * Chooses when the player tells the game their character.
     *
     * @param upFront {@code true} to choose before play, {@code false} to be
     *        asked once the game is over
     */
    void tellsCharacterUpFront(boolean upFront) {
        tellsCharacterUpFront = upFront;
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
