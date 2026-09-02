package com.guesswho.room;

import com.guesswho.game.AnswerCorrection;
import java.util.List;

/**
 * What both players are allowed to know once the game is over.
 *
 * <p>A separate type from {@link RoomState}, and that is the whole point.
 * {@code RoomState} is shaped so that no field on it <em>can</em> carry the
 * opponent's character — the guarantee lives in the type rather than in
 * everybody remembering. Adding a nullable character to it for the end of the
 * game would throw that away: the field would exist on every response of every
 * game, and the promise would go back to being something the server has to
 * remember to keep.</p>
 *
 * <p>So the reveal is its own type, reachable only from its own endpoint, and
 * only for a game that has finished. A type that cannot be built for a game in
 * progress cannot leak one.</p>
 *
 * <p>This is where the commitment made at the start finally pays for itself.
 * Online is the only mode where it means anything: the opponent's own client
 * answered every question about a character the server never saw, so the only
 * thing making those answers trustworthy is that they were promised in advance
 * and can now be checked against what was promised.</p>
 *
 * @param code     the room's code
 * @param winner   who won
 * @param you      the player asking, and how their own answers held up
 * @param opponent the other player, and how theirs did
 */
public record GameReveal(String code, String winner, Verified you, Verified opponent) {

    /**
     * One player's character, and whether their play matches it.
     *
     * <p>Two separate questions, and a player can fail either. Whether they
     * answered as the character they promised is what the commitment settles.
     * Whether they answered <em>correctly</em> for that character is the answer
     * review, and somebody can keep their promise while still having made a
     * mistake about their own card.</p>
     *
     * @param name             the player's username
     * @param character        who they were holding
     * @param promised         whether a commitment was recorded before play
     * @param keptTheirPromise whether that commitment matches this character
     * @param wrongAnswers     answers they gave that their character contradicts
     */
    public record Verified(
            String name,
            String character,
            boolean promised,
            boolean keptTheirPromise,
            List<AnswerCorrection> wrongAnswers) {

        /** Copies the list, so a reveal cannot be edited after the fact. */
        public Verified {
            wrongAnswers = wrongAnswers == null ? List.of() : List.copyOf(wrongAnswers);
        }

        /**
         * Whether this player's game holds up completely.
         *
         * @return true when they committed, kept to it, and answered consistently
         */
        public boolean isTrustworthy() {
            return promised && keptTheirPromise && wrongAnswers.isEmpty();
        }
    }
}
