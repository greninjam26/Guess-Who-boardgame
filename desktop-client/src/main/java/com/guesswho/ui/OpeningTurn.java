package com.guesswho.ui;

/**
 * Who takes the first turn, as offered by the setup screen.
 *
 * <p>The game package expresses this as two separate enums, one per game mode.
 * The interface offers a single list of buttons, so it carries a single choice
 * and lets {@link GameController} map it to whichever the mode requires.</p>
 */
enum OpeningTurn {
    /** The computer opponent starts. */
    COMPUTER,
    /** The player who entered their name first starts. */
    FIRST_PLAYER,
    /** The second player starts. */
    SECOND_PLAYER,
    /** Chosen at random. */
    RANDOM,
    /** The younger of the two players starts. */
    YOUNGER
}
