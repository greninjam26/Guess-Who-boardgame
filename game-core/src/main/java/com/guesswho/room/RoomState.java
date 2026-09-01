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
     * Starts building a room state with every field named.
     *
     * <p>Fifteen components, three of them adjacent booleans. Written
     * positionally that middle stretch reads {@code true, true, true}, which
     * says nothing about which of "they have chosen", "they are there" and "it
     * is your move" is which — and swapping two of them compiles perfectly and
     * shows a player the wrong board. The canonical constructor is still there
     * and still what Jackson uses, so nothing about the wire format changes.</p>
     *
     * @return a builder holding no fields yet
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the parts of a room state under their own names.
     *
     * <p>Every field is optional here on purpose: a room nobody has joined has
     * no game to describe, and listing a dozen nulls at that call site was the
     * other half of the problem this solves.</p>
     */
    public static final class Builder {
        private String code;
        private RoomStatus status;
        private String you;
        private String opponent;
        private String yourCharacter;
        private boolean opponentHasChosen;
        private boolean opponentPresent;
        private boolean yourTurn;
        private String currentPlayer;
        private String questionAwaitingYourAnswer;
        private String yourUnansweredQuestion;
        private List<AskedQuestion> yourQuestions = List.of();
        private List<AskedQuestion> opponentQuestions = List.of();
        private String winner;
        private Instant expiresAt;

        private Builder() {
        }

        /**
         * @param value the room's code
         * @return this builder
         */
        public Builder code(String value) {
            this.code = value;
            return this;
        }

        /**
         * @param value where the room has got to
         * @return this builder
         */
        public Builder status(RoomStatus value) {
            this.status = value;
            return this;
        }

        /**
         * @param value the name of whoever asked
         * @return this builder
         */
        public Builder you(String value) {
            this.you = value;
            return this;
        }

        /**
         * @param value the other player's name, or null if nobody has joined
         * @return this builder
         */
        public Builder opponent(String value) {
            this.opponent = value;
            return this;
        }

        /**
         * @param value the character you are holding, or null before you choose
         * @return this builder
         */
        public Builder yourCharacter(String value) {
            this.yourCharacter = value;
            return this;
        }

        /**
         * @param value whether they have chosen — never which
         * @return this builder
         */
        public Builder opponentHasChosen(boolean value) {
            this.opponentHasChosen = value;
            return this;
        }

        /**
         * @param value whether they have been heard from recently
         * @return this builder
         */
        public Builder opponentPresent(boolean value) {
            this.opponentPresent = value;
            return this;
        }

        /**
         * @param value whether it is your move
         * @return this builder
         */
        public Builder yourTurn(boolean value) {
            this.yourTurn = value;
            return this;
        }

        /**
         * @param value whose move it is, by name
         * @return this builder
         */
        public Builder currentPlayer(String value) {
            this.currentPlayer = value;
            return this;
        }

        /**
         * @param value what your opponent asked and is waiting on, else null
         * @return this builder
         */
        public Builder questionAwaitingYourAnswer(String value) {
            this.questionAwaitingYourAnswer = value;
            return this;
        }

        /**
         * @param value what you asked and have not been answered yet, else null
         * @return this builder
         */
        public Builder yourUnansweredQuestion(String value) {
            this.yourUnansweredQuestion = value;
            return this;
        }

        /**
         * @param value what you asked, and what you were told
         * @return this builder
         */
        public Builder yourQuestions(List<AskedQuestion> value) {
            this.yourQuestions = value;
            return this;
        }

        /**
         * @param value what they asked you, and what you answered
         * @return this builder
         */
        public Builder opponentQuestions(List<AskedQuestion> value) {
            this.opponentQuestions = value;
            return this;
        }

        /**
         * @param value who won, once somebody has
         * @return this builder
         */
        public Builder winner(String value) {
            this.winner = value;
            return this;
        }

        /**
         * @param value when the room is given up on
         * @return this builder
         */
        public Builder expiresAt(Instant value) {
            this.expiresAt = value;
            return this;
        }

        /**
         * @return the room state these parts describe
         */
        public RoomState build() {
            return new RoomState(code, status, you, opponent, yourCharacter,
                    opponentHasChosen, opponentPresent, yourTurn, currentPlayer,
                    questionAwaitingYourAnswer, yourUnansweredQuestion, yourQuestions,
                    opponentQuestions, winner, expiresAt);
        }
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
