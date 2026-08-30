package com.guesswho.game;

/**
 * How well the computer opponent plays.
 *
 * <p>Two things separate the levels: which question is asked, and how willing
 * the computer is to guess before it is certain. Waiting for certainty is safe
 * but slow, and a game is a race — spending another turn narrowing down while
 * the opponent guesses correctly loses a game that guessing might have won.</p>
 */
public enum ComputerDifficulty {
    /** Asks at random, and only guesses once one character is left. */
    EASY(1),
    /**
     * Asks the question that splits the remaining characters most evenly, and
     * will gamble on a coin flip between two rather than spend a turn.
     */
    HARD(2);

    private final int guessesAtOrBelow;

    ComputerDifficulty(int guessesAtOrBelow) {
        this.guessesAtOrBelow = guessesAtOrBelow;
    }

    /**
     * Reports whether the computer would guess rather than ask again.
     *
     * @param remainingCharacters how many characters it has not ruled out
     * @return {@code true} when it should guess now
     */
    boolean guessesWith(int remainingCharacters) {
        return remainingCharacters > 0 && remainingCharacters <= guessesAtOrBelow;
    }

    /**
     * Reports whether questions are chosen at random.
     *
     * @return {@code true} when the level does not think about which to ask
     */
    boolean asksAtRandom() {
        return this == EASY;
    }
}
