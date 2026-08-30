package com.guesswho.game;

/**
 * Difficulty levels available for a computer opponent.
 */
public enum ComputerDifficulty {
    /** Chooses questions randomly. */
    EASY("easy"),
    /** Chooses questions intended to eliminate about half of the candidates. */
    HARD("hard");

    private final String mode;

    ComputerDifficulty(String mode) {
        this.mode = mode;
    }

    String mode() {
        return mode;
    }
}
