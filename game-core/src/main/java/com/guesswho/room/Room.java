package com.guesswho.room;

import java.time.Instant;

/**
 * An online room, as the server holds it and the client is told about it.
 *
 * <p>Deliberately without the game state. What a client may see of a game in
 * progress depends on which player is asking, and a type that carries the whole
 * thing is one that will eventually be sent to the wrong person.</p>
 *
 * @param code      the six characters that open it
 * @param status    where it has got to
 * @param hostName  who created it
 * @param guestName who joined, or null while it is waiting
 * @param expiresAt when it will be given up on
 */
public record Room(
        String code,
        RoomStatus status,
        String hostName,
        String guestName,
        Instant expiresAt) {

    /** Whether somebody can still join with the code. */
    public boolean isWaiting() {
        return status == RoomStatus.WAITING;
    }
}
