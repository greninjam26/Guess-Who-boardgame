package com.guesswho.room;

import java.time.Instant;
import java.util.List;

/**
 * A game as one particular player is allowed to see it.
 *
 * <p>This type exists to make the dangerous thing impossible rather than
 * merely discouraged. There is no field on it that could hold the opponent's
 * character, so no amount of careless mapping can put one there — the guarantee
 * lives in the shape of the type rather than in everybody remembering.</p>
 *
 * <p>Sending the whole game to both clients and trusting each to hide half of
 * it would leave the answer readable off the wire by anybody, and the
 * commitment scheme in Phase 04 would be verifying a game that had already been
 * given away.</p>
 *
 * @param code              the room's code
 * @param status            where the room has got to
 * @param you               the name of whoever asked
 * @param opponent          the other player's name, or null if nobody has joined
 * @param yourCharacter     the character you are holding, or null before you choose
 * @param opponentHasChosen whether they have chosen — never which
 * @param opponentPresent   whether they have been heard from recently. False
 *                          means their game is probably closed; a player who is
 *                          merely thinking still has a client polling for them
 * @param yourTurn          whether it is your move
 * @param currentPlayer     whose move it is, by name
 * @param questionAwaitingYourAnswer what your opponent asked and is waiting on,
 *                          else null
 * @param yourUnansweredQuestion what you asked and have not been answered yet,
 *                          else null
 * @param yourQuestions     what you asked, and what you were told
 * @param opponentQuestions what they asked you, and what you answered
 * @param winner            who won, once somebody has
 * @param expiresAt         when the room is given up on
 */
public record RoomState(
        String code,
        RoomStatus status,
        String you,
        String opponent,
        String yourCharacter,
        boolean opponentHasChosen,
        boolean opponentPresent,
        boolean yourTurn,
        String currentPlayer,
        String questionAwaitingYourAnswer,
        String yourUnansweredQuestion,
        List<AskedQuestion> yourQuestions,
        List<AskedQuestion> opponentQuestions,
        String winner,
        Instant expiresAt) {

    /** Copies the question lists so a projection cannot be edited after the fact. */
    public RoomState {
        yourQuestions = yourQuestions == null ? List.of() : List.copyOf(yourQuestions);
        opponentQuestions =
                opponentQuestions == null ? List.of() : List.copyOf(opponentQuestions);
    }

    /**
     * One question and the answer it got.
     *
     * <p>Both sides' questions are public to both players: each was asked out
     * loud and answered out loud, and a player can already see their own board.
     * What stays private is only the character behind the answers.</p>
     *
     * @param question the question as asked
     * @param answer   the answer given
     */
    public record AskedQuestion(String question, boolean answer) {
    }
}
