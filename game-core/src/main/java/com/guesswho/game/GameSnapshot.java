package com.guesswho.game;

import java.util.List;
import java.util.Objects;

/**
 * Everything about a game in progress that is worth keeping.
 *
 * <p>Plain data, so that saving a game costs {@code game-core} no
 * dependencies. What to write it to, and in what format, is the caller's
 * business; this only says what there is to write.</p>
 *
 * <p>It holds what was decided rather than what follows from it. The
 * computer's tally of how many characters each question still splits, for
 * instance, follows from which characters it has ruled out, so only the
 * eliminations are here. Two records of the same fact can disagree, and the
 * one that is written down would win.</p>
 *
 * @param status                  where the game had got to
 * @param winner                  the winner's name once there is one, else null
 * @param questionMode            how questions were being chosen
 * @param computerDifficulty      how the computer was playing, null in a
 *                                two-player game
 * @param pendingComputerQuestion the question the computer has asked and is
 *                                waiting to hear the answer to, else null
 * @param pendingQuestionAsker    the player waiting on an answer, else null.
 *                                Without this a question asked on one machine
 *                                disappears before the other machine is told
 *                                about it
 * @param pendingQuestionText     what they asked, else null
 * @param firstPlayer             the player who set the game up
 * @param secondPlayer            the second player, null when the opponent is
 *                                the computer
 * @param computer                the computer opponent, null in a two-player
 *                                game
 */
public record GameSnapshot(
        GameStatus status,
        String winner,
        QuestionMode questionMode,
        ComputerDifficulty computerDifficulty,
        String pendingComputerQuestion,
        String pendingQuestionAsker,
        String pendingQuestionText,
        PlayerState firstPlayer,
        PlayerState secondPlayer,
        ComputerState computer) {

    /** Rejects the two shapes a game cannot have. */
    public GameSnapshot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(firstPlayer, "firstPlayer");
        if (secondPlayer == null && computer == null) {
            throw new IllegalArgumentException("A game needs an opponent");
        }
        if (secondPlayer != null && computer != null) {
            throw new IllegalArgumentException(
                    "A game has one opponent, not both a player and the computer");
        }
    }

    /** True when the opponent was the computer. */
    public boolean isAgainstComputer() {
        return computer != null;
    }

    /**
     * One player's side of the game.
     *
     * <p>The questions and the answers are kept as two lists in step rather
     * than as pairs, because that is how the player holds them and replaying
     * them in order is what rebuilds the rest: which questions remain unasked
     * follows from which have been asked.</p>
     *
     * @param username         the player's name, null for the computer
     * @param birthday         the birthday value used to pick who started
     * @param selectedCharacter the character being held, null if not yet chosen
     * @param commitmentHash   the promise made when it was chosen, else null
     * @param commitmentNonce  the salt for that promise, else null
     * @param questionsAsked   the questions asked, oldest first
     * @param questionAnswers  the answers received, in the same order
     * @param isTurn           whether it was this player's turn
     */
    public record PlayerState(
            String username,
            int birthday,
            String selectedCharacter,
            String commitmentHash,
            String commitmentNonce,
            List<String> questionsAsked,
            List<Boolean> questionAnswers,
            boolean isTurn) {

        /** Copies the lists, so a snapshot cannot change under whoever holds it. */
        public PlayerState {
            questionsAsked = List.copyOf(Objects.requireNonNull(questionsAsked, "questionsAsked"));
            questionAnswers =
                    List.copyOf(Objects.requireNonNull(questionAnswers, "questionAnswers"));
            if (questionsAsked.size() != questionAnswers.size()) {
                throw new IllegalArgumentException(
                        "Every question asked has an answer: " + questionsAsked.size()
                                + " questions but " + questionAnswers.size() + " answers");
            }
        }

        /** The commitment made when the character was chosen, if one was. */
        CharacterCommitment commitment() {
            return commitmentHash == null || commitmentNonce == null
                    ? null
                    : new CharacterCommitment(commitmentHash, commitmentNonce);
        }
    }

    /**
     * The computer opponent, which is a player plus what it has worked out.
     *
     * @param player   the computer's own side of the game
     * @param ruledOut board positions the computer has eliminated
     */
    public record ComputerState(PlayerState player, List<Integer> ruledOut) {

        /** Copies the list for the same reason {@link PlayerState} does. */
        public ComputerState {
            Objects.requireNonNull(player, "player");
            ruledOut = List.copyOf(Objects.requireNonNull(ruledOut, "ruledOut"));
        }
    }
}
