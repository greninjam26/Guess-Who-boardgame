package com.guesswho.room;

/**
 * Where an online room has got to.
 */
public enum RoomStatus {
    /** Created, and waiting for a second player to join with the code. */
    WAITING,
    /** Both players are in and the game is being played. */
    IN_PROGRESS,
    /** Played out. Kept briefly so both players can see how it ended. */
    FINISHED,
    /** Nobody joined, or nobody moved, for long enough that it was given up on. */
    EXPIRED
}
