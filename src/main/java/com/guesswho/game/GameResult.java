package com.guesswho.game;

import java.util.List;

/**
 * Immutable snapshot of a completed game for persistence and other external
 * consumers.
 *
 * @param participants participants in play order
 * @param winner winning participant name
 * @param mode how the game was played
 * @param difficulty computer difficulty, or {@code null} outside a
 *        player-versus-computer game
 */
public record GameResult(
        List<Participant> participants,
        String winner,
        GameMode mode,
        ComputerDifficulty difficulty) {
    /**
     * Creates a result with an immutable participant list.
     */
    public GameResult {
        participants = List.copyOf(participants);
    }

    /**
     * Immutable result data for one game participant.
     *
     * @param name participant display name
     * @param selectedCharacter selected character name
     * @param questionAnswers questions asked and answers received
     */
    public record Participant(
            String name,
            String selectedCharacter,
            List<QuestionAnswer> questionAnswers) {
        /**
         * Creates a participant result with an immutable question history.
         */
        public Participant {
            questionAnswers = List.copyOf(questionAnswers);
        }
    }

    /**
     * One question and answer from a participant's game history.
     *
     * @param question question text
     * @param answer answer received
     */
    public record QuestionAnswer(String question, boolean answer) {
    }
}
