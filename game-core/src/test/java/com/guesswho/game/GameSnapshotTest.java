package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameSnapshotTest {
    @Test
    void aGameThatHasNotStartedHasNothingToSave() {
        assertThrows(IllegalStateException.class, () -> new Game().snapshot());
    }

    @Test
    void carriesTheSetupAcross() throws Exception {
        Game game = computerGame();

        Game resumed = Game.restoredFrom(game.snapshot());

        assertEquals("sam", resumed.getFirstPlayer().getUsername());
        assertEquals(GameStatus.IN_PROGRESS, resumed.getStatus());
        assertNotNull(resumed.getComputerPlayer());
        assertNull(resumed.getSecondPlayer());
    }

    @Test
    void remembersWhoseTurnItIs() throws Exception {
        Game game = computerGame();
        game.getFirstPlayer().setIsTurn(false);
        game.getComputerPlayer().setIsTurn(true);

        Game resumed = Game.restoredFrom(game.snapshot());

        assertFalse(resumed.getFirstPlayer().getIsTurn());
        assertTrue(resumed.getComputerPlayer().getIsTurn());
    }

    @Test
    void remembersTheQuestionsAlreadyAskedAndTheirAnswers() throws Exception {
        Game game = computerGame();
        game.getFirstPlayer().recordQuestionAnswer("Does your character wear glasses?", true);
        game.getFirstPlayer().recordQuestionAnswer("Is the person wearing a hat?", false);

        Game resumed = Game.restoredFrom(game.snapshot());

        assertEquals(2, resumed.getFirstPlayer().getQuestionsAsked().size());
        assertEquals("Does your character wear glasses?",
                resumed.getFirstPlayer().getQuestionsAsked().get(0).getQuestion());
        assertEquals(List.of(true, false), resumed.getFirstPlayer().getQuestionAnswers());
    }

    @Test
    void doesNotOfferAQuestionTheresumedPlayerHasAlreadyAsked() throws Exception {
        Game game = computerGame();
        game.getFirstPlayer().recordQuestionAnswer("Does your character wear glasses?", true);
        int remainingBefore = game.getFirstPlayer().getUnAskedQuestions().size();

        Game resumed = Game.restoredFrom(game.snapshot());

        assertEquals(remainingBefore, resumed.getFirstPlayer().getUnAskedQuestions().size(),
                "The unasked list is derived from the asked one and must come back in step");
        assertTrue(resumed.getFirstPlayer().getUnAskedQuestions().stream()
                        .noneMatch(q -> q.getQuestion().equals("Does your character wear glasses?")),
                "A question already asked should not be offered again after resuming");
    }

    @Test
    void remembersWhatTheComputerHasRuledOut() throws Exception {
        Game game = computerGame();
        game.getComputerPlayer().ruleOut(0);
        game.getComputerPlayer().ruleOut(5);
        game.getComputerPlayer().ruleOut(11);
        int remaining = game.getComputerPlayer().getPossibleCharacters().size();

        Game resumed = Game.restoredFrom(game.snapshot());

        assertEquals(remaining, resumed.getComputerPlayer().getPossibleCharacters().size());
        assertTrue(resumed.getComputerPlayer().getPossibleCharacters().stream()
                        .noneMatch(c -> c.getCharacterIndex() == 5),
                "A character the computer had eliminated came back after resuming");
    }

    @Test
    void theComputerKeepsPlayingSensiblyAfterResuming() throws Exception {
        //The tally behind question choice is derived from the eliminations. If
        //restoring copied it across instead of rebuilding it, the computer
        //would still answer questions but would choose them against a board it
        //no longer has.
        Game game = computerGame();
        game.getFirstPlayer().setIsTurn(false);
        game.getComputerPlayer().setIsTurn(true);
        for (int index = 0; index < 20; index++) {
            game.getComputerPlayer().ruleOut(index);
        }

        Game resumed = Game.restoredFrom(game.snapshot());
        Question chosen = resumed.playComputerQuestion();

        assertNotNull(chosen);
        assertEquals(4, resumed.getComputerPlayer().getPossibleCharacters().size());
    }

    @Test
    void remembersTheCharacterAndThePromiseMadeAboutIt() throws Exception {
        Game game = computerGame();
        game.selectCharacter("sam", "Olivia");
        CharacterCommitment promised = game.getFirstPlayer().getCommitment();

        Game resumed = Game.restoredFrom(game.snapshot());

        assertEquals("Olivia", resumed.getFirstPlayer().getSelectedCharacter().getName());
        assertEquals(promised.hash(), resumed.getFirstPlayer().getCommitment().hash());
        assertTrue(resumed.getFirstPlayer().getCommitment().matches("Olivia"),
                "A promise that stops verifying after a resume is worse than none");
    }

    @Test
    void carriesATwoPlayerGameAcrossWithBothPlayers() throws Exception {
        Game game = new Game();
        game.startPlayerGame("sam", 1, "alex", 2,
                PlayerGameStart.SECOND_PLAYER, QuestionMode.PRESET);
        game.getSecondPlayer().recordQuestionAnswer("Is the person wearing a hat?", true);

        Game resumed = Game.restoredFrom(game.snapshot());

        assertEquals("sam", resumed.getFirstPlayer().getUsername());
        assertEquals("alex", resumed.getSecondPlayer().getUsername());
        assertNull(resumed.getComputerPlayer());
        assertTrue(resumed.getSecondPlayer().getIsTurn());
        assertEquals(1, resumed.getSecondPlayer().getQuestionsAsked().size());
    }

    @Test
    void keepsTheQuestionModeSoFreeQuestionsStayAvailable() throws Exception {
        Game game = new Game();
        game.startPlayerGame("sam", 1, "alex", 2,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.FREE_FORM);

        assertEquals(QuestionMode.FREE_FORM, Game.restoredFrom(game.snapshot())
                .snapshot().questionMode());
    }

    @Test
    void refusesASnapshotWithNoOpponent() {
        assertThrows(IllegalArgumentException.class, () -> new GameSnapshot(
                GameStatus.IN_PROGRESS, null, QuestionMode.PRESET, null, null,
                player("sam"), null, null));
    }

    @Test
    void refusesASnapshotWithTwoKindsOfOpponent() {
        assertThrows(IllegalArgumentException.class, () -> new GameSnapshot(
                GameStatus.IN_PROGRESS, null, QuestionMode.PRESET, null, null,
                player("sam"), player("alex"),
                new GameSnapshot.ComputerState(player(null), List.of())));
    }

    @Test
    void refusesAnAnswerWithoutItsQuestion() {
        assertThrows(IllegalArgumentException.class, () -> new GameSnapshot.PlayerState(
                "sam", 0, null, null, null,
                List.of("Is the person wearing a hat?"), List.of(), false));
    }

    private static Game computerGame() throws Exception {
        Game game = new Game();
        game.startComputerGame("sam", ComputerDifficulty.HARD,
                ComputerGameStart.PLAYER, QuestionMode.PRESET);
        return game;
    }

    private static GameSnapshot.PlayerState player(String username) {
        return new GameSnapshot.PlayerState(
                username, 0, null, null, null, List.of(), List.of(), false);
    }
}
