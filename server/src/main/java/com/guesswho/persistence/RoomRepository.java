package com.guesswho.persistence;

import com.guesswho.room.RoomStatus;
import java.time.Instant;
import java.util.Optional;

/**
 * Stores online rooms and the games inside them.
 *
 * <p>Rooms live in the database rather than in memory. In-memory sessions die
 * on every restart, so deploying while two people are mid-game would destroy
 * their game — which is the reason for this, not scale.</p>
 */
public interface RoomRepository {
    /**
     * Creates a room waiting for somebody to join.
     *
     * @param code          the code that opens it
     * @param hostAccountId who created it
     * @param expiresAt     when it is given up on if nobody joins
     * @return the stored room
     * @throws CodeTakenException if that code is already in use
     */
    StoredRoom create(String code, long hostAccountId, Instant expiresAt);

    /**
     * Finds a room by its code.
     *
     * @param code the code somebody typed
     * @return the room, or empty when no such room exists
     */
    Optional<StoredRoom> findByCode(String code);

    /**
     * Puts a second player into a waiting room.
     *
     * <p>Conditional on the room still waiting, so that two people racing to
     * join with the same code cannot both succeed.</p>
     *
     * @param code           the room's code
     * @param guestAccountId who is joining
     * @param gameState      the game as it starts
     * @param expiresAt      the new deadline, now that it is being played
     * @return true when this caller was the one who joined
     */
    boolean join(String code, long guestAccountId, String gameState, Instant expiresAt);

    /**
     * Replaces the stored game and pushes the deadline out.
     *
     * @param code      the room's code
     * @param gameState the game after a move
     * @param status    where the room has got to
     * @param expiresAt the new deadline
     */
    void updateGame(String code, String gameState, RoomStatus status, Instant expiresAt);

    /**
     * How many rooms an account currently has open.
     *
     * <p>A rate limit bounds how fast rooms appear; only a cap bounds how many
     * exist at once.</p>
     *
     * @param hostAccountId the account to count for
     * @return the number of rooms waiting or in progress
     */
    int openRoomCount(long hostAccountId);

    /**
     * Deletes rooms whose time is up.
     *
     * @param now the moment to judge against
     * @return how many were removed
     */
    int deleteExpired(Instant now);

    /**
     * A room as stored, including the game nobody outside the server may see whole.
     *
     * @param code           the code that opens it
     * @param status         where it has got to
     * @param hostAccountId  who created it
     * @param hostName       their username
     * @param guestAccountId who joined, or null
     * @param guestName      their username, or null
     * @param gameState      the serialised game, or null while waiting
     * @param expiresAt      when it is given up on
     */
    record StoredRoom(
            String code,
            RoomStatus status,
            long hostAccountId,
            String hostName,
            Long guestAccountId,
            String guestName,
            String gameState,
            Instant expiresAt) {

        /** Whether an account is one of the two people in this room. */
        public boolean includes(long accountId) {
            return hostAccountId == accountId
                    || (guestAccountId != null && guestAccountId == accountId);
        }
    }

    /** Thrown when a generated code is already in use. */
    class CodeTakenException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * @param code the code somebody else already has
         */
        public CodeTakenException(String code) {
            super("Room code is already in use: " + code);
        }
    }
}
