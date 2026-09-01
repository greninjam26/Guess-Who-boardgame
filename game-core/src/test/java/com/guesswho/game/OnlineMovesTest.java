package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnlineMovesTest {
    private Game game;

    @BeforeEach
    void twoPlayers() throws Exception {
        game = new Game();
        game.startPlayerGame("host", 0, "guest", 0,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.selectCharacter("host", "Olivia");
        game.selectCharacter("guest", "Sam");
    }

    @Test
    void holdsAQuestionUntilTheOtherPlayerAnswersIt() {
        game.askQuestion("host", "Does your character wear glasses?");

        assertTrue(game.getPendingPlayerQuestion().isPresent());
        assertEquals("host", game.getPendingPlayerQuestion().orElseThrow().asker());
    }

    @Test
    void doesNotPassTheTurnUntilTheQuestionIsAnswered() {
        //Otherwise one player could ask five questions while the other is
        //still deciding how to answer the first.
        game.askQuestion("host", "Does your character wear glasses?");

        assertEquals("host", game.getCurrentPlayerName());
    }

    @Test
    void passesTheTurnOnceItIsAnswered() {
        game.askQuestion("host", "Does your character wear glasses?");

        game.answerQuestion("guest", true);

        assertEquals("guest", game.getCurrentPlayerName());
        assertTrue(game.getPendingPlayerQuestion().isEmpty());
    }

    @Test
    void recordsTheAnswerAgainstWhoeverAsked() {
        //It is the asker's board the answer narrows.
        game.askQuestion("host", "Does your character wear glasses?");

        game.answerQuestion("guest", true);

        assertEquals(1, game.getFirstPlayer().getQuestionsAsked().size());
        assertEquals(List.of(true), game.getFirstPlayer().getQuestionAnswers());
        assertTrue(game.getSecondPlayer().getQuestionsAsked().isEmpty());
    }

    @Test
    void refusesAQuestionOutOfTurn() {
        assertThrows(IllegalStateException.class,
                () -> game.askQuestion("guest", "Does your character wear glasses?"));
    }

    @Test
    void refusesASecondQuestionWhileOneIsWaiting() {
        game.askQuestion("host", "Does your character wear glasses?");

        assertThrows(IllegalStateException.class,
                () -> game.askQuestion("host", "Is the person wearing a hat?"));
    }

    @Test
    void refusesToLetSomebodyAnswerTheirOwnQuestion() {
        //Answering your own question is narrowing your own board however you
        //like, which is the whole game.
        game.askQuestion("host", "Does your character wear glasses?");

        assertThrows(IllegalArgumentException.class, () -> game.answerQuestion("host", true));
    }

    @Test
    void refusesAnAnswerWhenNothingWasAsked() {
        assertThrows(IllegalStateException.class, () -> game.answerQuestion("guest", true));
    }

    @Test
    void refusesABlankQuestion() {
        assertThrows(IllegalArgumentException.class, () -> game.askQuestion("host", "  "));
    }

    @Test
    void refusesAQuestionFromSomebodyNotInTheGame() {
        assertThrows(IllegalArgumentException.class,
                () -> game.askQuestion("stranger", "Does your character wear glasses?"));
    }

    @Test
    void decidesForItselfWhetherAGuessIsRight() {
        //Nobody is asked to confirm. Two people on separate machines cannot see
        //each other, and asking the loser to agree that they lost is not a
        //check worth having.
        assertEquals("host", game.guessOpponent("host", "Sam"));
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    @Test
    void givesTheGameToTheOpponentWhenAGuessIsWrong() {
        assertEquals("guest", game.guessOpponent("host", "Nick"));
    }

    @Test
    void refusesAGuessOutOfTurn() {
        assertThrows(IllegalStateException.class, () -> game.guessOpponent("guest", "Olivia"));
    }

    @Test
    void refusesAGuessWhileAQuestionIsWaiting() {
        game.askQuestion("host", "Does your character wear glasses?");

        assertThrows(IllegalStateException.class, () -> game.guessOpponent("host", "Sam"));
    }

    @Test
    void refusesAGuessAtSomebodyNotOnTheBoard() {
        assertThrows(IllegalArgumentException.class,
                () -> game.guessOpponent("host", "Gandalf"));
    }

    @Test
    void refusesAGuessBeforeTheOpponentHasChosen() throws Exception {
        //Otherwise somebody wins against a character nobody was holding.
        Game unchosen = new Game();
        unchosen.startPlayerGame("host", 0, "guest", 0,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class,
                () -> unchosen.guessOpponent("host", "Sam"));
    }

    @Test
    void carriesAWaitingQuestionThroughBeingStoredAndRestored() throws Exception {
        //The two players are on two machines, so the question is written to the
        //database by one request and read by another. Losing it there would
        //leave the second player with nothing to answer.
        game.askQuestion("host", "Does your character wear glasses?");

        Game restored = Game.restoredFrom(game.snapshot());

        assertEquals("host", restored.getPendingPlayerQuestion().orElseThrow().asker());
        assertEquals("Does your character wear glasses?",
                restored.getPendingPlayerQuestion().orElseThrow().question());
    }

    @Test
    void aRestoredGameCanStillBeAnswered() {
        game.askQuestion("host", "Does your character wear glasses?");

        Game restored = restore();
        restored.answerQuestion("guest", true);

        assertEquals("guest", restored.getCurrentPlayerName());
        assertEquals(1, restored.getFirstPlayer().getQuestionsAsked().size());
    }

    @Test
    void hasNoWaitingQuestionWhenNoneWasAsked() {
        assertFalse(restore().getPendingPlayerQuestion().isPresent());
    }

    private Game restore() {
        try {
            return Game.restoredFrom(game.snapshot());
        }
        catch (Exception unrestorable) {
            throw new AssertionError(unrestorable);
        }
    }
}
