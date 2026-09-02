package com.guesswho.client;

import com.guesswho.room.Room;
import com.guesswho.room.GameReveal;
import com.guesswho.room.RoomState;
import java.util.concurrent.CompletableFuture;

/**
 * Playing an online game, from the game's side.
 */
public interface OnlineGameClient {
    /**
     * Opens a room and gets the code to share.
     *
     * @param token the session token
     * @return the new room
     */
    CompletableFuture<OnlineOutcome<Room>> createRoom(String token);

    /**
     * Joins somebody else's room.
     *
     * @param code  the code they shared
     * @param token the session token
     * @return the room joined
     */
    CompletableFuture<OnlineOutcome<Room>> joinRoom(String code, String token);

    /**
     * Reads the game as this player may see it.
     *
     * @param code  the room's code
     * @param token the session token
     * @return their view of the game
     */
    CompletableFuture<OnlineOutcome<RoomState>> state(String code, String token);

    /**
     * Chooses the character this player will be guessed at.
     *
     * @param code      the room's code
     * @param character the character they are holding
     * @param token     the session token
     * @return their view of the game afterwards
     */
    /**
     * Asks for a finished game's ending: both characters, both promises, and
     * how the answers held up.
     *
     * <p>Separate from {@link #state}, because the state of a game in progress
     * is built so it cannot carry the opponent's character. This is the only
     * call that can, and the server refuses it until the game is over.</p>
     *
     * @param code  the room's code
     * @param token the session token
     * @return the ending, or a refusal while the game is still being played
     */
    CompletableFuture<OnlineOutcome<GameReveal>> reveal(String code, String token);

    CompletableFuture<OnlineOutcome<RoomState>> chooseCharacter(
            String code, String character, String token);

    /**
     * Asks the opponent a question.
     *
     * @param code     the room's code
     * @param question what to ask
     * @param token    the session token
     * @return their view of the game afterwards
     */
    CompletableFuture<OnlineOutcome<RoomState>> ask(String code, String question, String token);

    /**
     * Answers the question the opponent asked.
     *
     * @param code   the room's code
     * @param answer yes or no
     * @param token  the session token
     * @return their view of the game afterwards
     */
    CompletableFuture<OnlineOutcome<RoomState>> answer(String code, boolean answer, String token);

    /**
     * Guesses the opponent's character, which ends the game either way.
     *
     * @param code      the room's code
     * @param character who they think it is
     * @param token     the session token
     * @return their view of the game afterwards
     */
    CompletableFuture<OnlineOutcome<RoomState>> guess(String code, String character, String token);
}
