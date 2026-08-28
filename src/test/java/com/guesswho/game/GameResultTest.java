package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameResultTest {
    @Test
    void copiesMutableInputCollections() {
        ArrayList<GameResult.QuestionAnswer> questionAnswers = new ArrayList<>();
        questionAnswers.add(new GameResult.QuestionAnswer("Is your character wearing a hat?", true));
        GameResult.Participant participant = new GameResult.Participant(
                "Player", "Olivia", questionAnswers);
        ArrayList<GameResult.Participant> participants = new ArrayList<>();
        participants.add(participant);

        GameResult result = new GameResult(participants, "Player");
        questionAnswers.clear();
        participants.clear();

        assertEquals(1, result.participants().size());
        assertEquals(1, result.participants().get(0).questionAnswers().size());
    }

    @Test
    void exposesUnmodifiableCollections() {
        GameResult.QuestionAnswer questionAnswer = new GameResult.QuestionAnswer(
                "Is your character wearing a hat?", true);
        GameResult.Participant participant = new GameResult.Participant(
                "Player", "Olivia", List.of(questionAnswer));
        GameResult result = new GameResult(List.of(participant), "Player");

        assertThrows(UnsupportedOperationException.class,
                () -> result.participants().add(participant));
        assertThrows(UnsupportedOperationException.class,
                () -> result.participants().get(0).questionAnswers().add(questionAnswer));
    }
}
