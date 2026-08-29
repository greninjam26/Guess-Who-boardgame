package com.guesswho.game;

/**
 * How a completed game was played.
 */
public enum GameMode {
    /** One human against the computer opponent. */
    PVE,
    /** Two humans sharing one machine. */
    PVP_LOCAL,
    /** Two humans on separate machines through the server. */
    PVP_ONLINE
}
