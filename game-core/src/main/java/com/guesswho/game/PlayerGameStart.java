package com.guesswho.game;

/**
 * Opening-turn choices for a two-player game.
 */
public enum PlayerGameStart {
    /** The first player takes the opening turn. */
    FIRST_PLAYER,
    /** The second player takes the opening turn. */
    SECOND_PLAYER,
    /** The opening player is selected randomly. */
    RANDOM,
    /** The younger player starts; equal birthdays are resolved randomly. */
    YOUNGER
}
