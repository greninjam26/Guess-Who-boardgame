package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlayerTest {
    @Test
    void rejectsUnknownCharacterNames() throws Exception {
        Player player = new Player("");

        assertThrows(IllegalArgumentException.class,
                () -> player.findCharacter("Unknown character"));
    }

    @Test
    void initializesFromTheBoardCollections() throws Exception {
        Board reducedBoard = new Board();
        reducedBoard.getQuestionsList().remove(reducedBoard.getQuestionsList().size() - 1);

        Player player = new Player("", reducedBoard);

        assertEquals(18, player.getUnAskedQuestions().size());
    }

    @Test
    void startsWithNoCharacterChosen() throws Exception {
        Player player = new Player("");

        assertNull(player.getSelectedCharacter(),
                "A human chooses their own character; nothing should pick one for them");
    }

    @Test
    void recordsAFreeFormQuestionAndAnswerTogether() throws Exception {
        Player player = new Player("");

        player.recordQuestionAnswer("Does your character look friendly?", false);

        assertEquals("Does your character look friendly?",
                player.getQuestionsAsked().get(0).getQuestion());
        assertEquals(false, player.getQuestionAnswers().get(0));
    }
}
