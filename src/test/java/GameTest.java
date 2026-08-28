import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameTest {
    private Game game;

    @BeforeEach
    void createGame() {
        game = new Game();
    }

    @Test
    void newGameStartsWithoutAWinner() {
        assertEquals(GameStatus.STARTING, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void playerCanStartBeforeComputer() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getComputerPlayer().getIsTurn());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }

    @Test
    void computerCanStartBeforePlayer() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.HARD, ComputerGameStart.COMPUTER);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void randomComputerGameStartUsesInjectedRandomness() throws Exception {
        game = new Game(new Random() {
            @Override
            public boolean nextBoolean() {
                return false;
            }
        });

        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.RANDOM);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void computerGameRejectsBlankUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startComputerGame("  ", ComputerDifficulty.EASY, ComputerGameStart.PLAYER));
    }

    @Test
    void computerGameRejectsReservedComputerUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startComputerGame("AI", ComputerDifficulty.EASY, ComputerGameStart.PLAYER));
    }

    @Test
    void askingComputerRecordsQuestionAndAnswer() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Sam"));

        String answer = game.askComputer("Is your character's eye colour green?");

        assertEquals("Yes", answer);
        assertEquals(1, game.getFirstPlayer().getQuestionsAsked().size());
        assertTrue(game.getFirstPlayer().getQuestionAnswers().get(0));
        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void gameOwnsComputerQuestionAndAnswerFlow() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);

        Question question = game.playComputerQuestion();
        game.answerComputerQuestion(false);

        assertSame(question, game.getComputerPlayer().getQuestionsAsked().get(0));
        assertFalse(game.getComputerPlayer().getQuestionAnswers().get(0));
        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void computerQuestionCannotBePlayedInAPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());
    }

    @Test
    void computerAnswerCannotBeRecordedBeforeAQuestion() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);

        assertThrows(IllegalStateException.class, () -> game.answerComputerQuestion(true));
    }

    @Test
    void computerMustAnswerPendingQuestionBeforePlayingAnother() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);
        game.playComputerQuestion();

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());

        assertEquals(1, game.getComputerPlayer().getQuestionsAsked().size());
    }

    @Test
    void turnCannotAdvanceWhileComputerQuestionIsPending() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);
        game.playComputerQuestion();

        assertThrows(IllegalStateException.class, () -> game.advanceTurn());

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
        assertEquals(1, game.getComputerPlayer().getQuestionsAsked().size());
        assertTrue(game.getComputerPlayer().getQuestionAnswers().isEmpty());
    }

    @Test
    void computerCannotReuseAnAnsweredQuestionOnALaterTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);
        game.playComputerQuestion();
        game.answerComputerQuestion(false);
        game.advanceTurn();

        assertThrows(IllegalStateException.class, () -> game.answerComputerQuestion(true));

        assertEquals(1, game.getComputerPlayer().getQuestionAnswers().size());
    }

    @Test
    void computerCannotPlayAfterAllQuestionsAreExhausted() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);
        int availableQuestions = game.getComputerPlayer().getUnAskedQuestions().size();
        for (int index = 0; index < availableQuestions; index++) {
            game.playComputerQuestion();
            game.answerComputerQuestion(false);
            game.advanceTurn();
        }

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());
    }

    @Test
    void playerCannotAskComputerDuringComputerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);

        assertThrows(
                IllegalStateException.class,
                () -> game.askComputer("Is your character's eye colour green?"));

        assertTrue(game.getFirstPlayer().getQuestionsAsked().isEmpty());
    }

    @Test
    void computerCannotPlayQuestionDuringPlayerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());

        assertTrue(game.getComputerPlayer().getQuestionsAsked().isEmpty());
    }

    @Test
    void correctComputerGuessFinishesGameWithPlayerAsWinner() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Sam"));

        String result = game.guessComputer("Sam");

        assertTrue(result.contains("you won"));
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player", game.getWinner().orElseThrow());
    }

    @Test
    void incorrectComputerGuessFinishesGameWithComputerAsWinner() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Sam"));

        String result = game.guessComputer("Olivia");

        assertTrue(result.contains("you lost"));
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("AI", game.getWinner().orElseThrow());
    }

    @Test
    void computerGuessCannotBeMadeBeforeGameStarts() {
        assertThrows(IllegalStateException.class, () -> game.guessComputer("Sam"));
    }

    @Test
    void computerGuessCannotBeMadeInAPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        assertThrows(IllegalStateException.class, () -> game.guessComputer("Sam"));
    }

    @Test
    void playerCannotGuessComputerDuringComputerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER);

        assertThrows(IllegalStateException.class, () -> game.guessComputer("Sam"));

        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void computerGuessRejectsBlankNameWithoutFinishingGame() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);

        assertThrows(IllegalArgumentException.class, () -> game.guessComputer("  "));

        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void youngerPlayerStartsWhenBirthdayDeterminesTurn() throws Exception {
        game.startPlayerGame(
                "Younger", 20050101,
                "Older", 19950101,
                PlayerGameStart.YOUNGER);

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getSecondPlayer().getIsTurn());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }

    @Test
    void firstPlayerCanStartPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void secondPlayerCanStartPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.SECOND_PLAYER);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void playerLookupReturnsPlayerWithMatchingUsername() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        assertSame(game.getSecondPlayer(), game.getPlayer("Player 2"));
    }

    @Test
    void playerLookupRejectsUnknownUsername() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        assertThrows(IllegalArgumentException.class, () -> game.getPlayer("Unknown"));
    }

    @Test
    void gameProvidesCharacterNamesForChoices() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);

        String[] characterNames = game.getCharacterNames();

        assertArrayEquals(new String[] {
                "Sam", "Olivia", "Nick", "David", "Sofia", "Liz",
                "Lily", "Leo", "Emma", "Daniel", "Ben", "Katie",
                "Al", "Amy", "Mike", "Gabe", "Farah", "Laura",
                "Jordan", "Eric", "Carmen", "Rachel", "Joe", "Mia"
        }, characterNames);
    }

    @Test
    void characterNamesAreUnavailableBeforeGameStarts() {
        assertThrows(IllegalStateException.class, () -> game.getCharacterNames());
    }

    @Test
    void gameSelectsCharacterForNamedPlayer() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);
        game.finish("Player 1");

        game.selectCharacter("Player 2", "Sam");

        assertEquals("Sam", game.getSecondPlayer().getSelectedCharacter().getName());
    }

    @Test
    void gameProvidesSelectedCharacterIndexForNamedPlayer() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);
        game.finish("Player 1");
        game.selectCharacter("Player 2", "Sam");

        assertEquals(0, game.getSelectedCharacterIndex("Player 2"));
    }

    @Test
    void selectedCharacterIndexIsHiddenUntilGameFinishes() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);

        assertThrows(
                IllegalStateException.class,
                () -> game.getSelectedCharacterIndex("Player"));
    }

    @Test
    void characterCannotBeSelectedUntilGameFinishes() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);
        Character originalCharacter = game.getFirstPlayer().getSelectedCharacter();
        String differentCharacterName = originalCharacter.getName().equals("Sam") ? "Olivia" : "Sam";

        assertThrows(
                IllegalStateException.class,
                () -> game.selectCharacter("Player 1", differentCharacterName));
        assertSame(originalCharacter, game.getFirstPlayer().getSelectedCharacter());
    }

    @Test
    void characterSelectionRejectsUnknownUsername() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);
        game.finish("Player");

        assertThrows(
                IllegalArgumentException.class,
                () -> game.selectCharacter("Unknown", "Sam"));
    }

    @Test
    void characterSelectionRejectsUnknownCharacterWithoutChangingSelection() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER);
        game.finish("Player");
        Character originalCharacter = game.getFirstPlayer().getSelectedCharacter();

        assertThrows(
                IllegalArgumentException.class,
                () -> game.selectCharacter("Player", "Unknown character"));
        assertSame(originalCharacter, game.getFirstPlayer().getSelectedCharacter());
    }

    @Test
    void gameRecordsAPlayerQuestionAndAnswer() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        game.recordPlayerQuestion(
                "Player 1", "Does your character look friendly?", false);

        assertEquals("Does your character look friendly?",
                game.getFirstPlayer().getQuestionsAsked().get(0).getQuestion());
        assertFalse(game.getFirstPlayer().getQuestionAnswers().get(0));
    }

    @Test
    void playerQuestionCannotBeRecordedBeforeGameStarts() {
        assertThrows(
                IllegalStateException.class,
                () -> game.recordPlayerQuestion("Player 1", "Is this too early?", true));
    }

    @Test
    void inactivePlayerCannotRecordAQuestion() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        assertThrows(
                IllegalStateException.class,
                () -> game.recordPlayerQuestion("Player 2", "Am I out of turn?", true));

        assertTrue(game.getSecondPlayer().getQuestionsAsked().isEmpty());
    }

    @Test
    void advancingTurnSwitchesBetweenHumanPlayers() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        game.advanceTurn();

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getSecondPlayer().getIsTurn());

        game.advanceTurn();

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void turnCannotAdvanceBeforeGameStarts() {
        assertThrows(IllegalStateException.class, () -> game.advanceTurn());
    }

    @Test
    void randomPlayerGameStartUsesInjectedRandomness() throws Exception {
        game = new Game(new Random() {
            @Override
            public boolean nextBoolean() {
                return false;
            }
        });

        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.RANDOM);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void playerGameRejectsBlankUsernames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startPlayerGame(
                        "  ", 20000101,
                        "Player 2", 20010101,
                        PlayerGameStart.FIRST_PLAYER));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startPlayerGame(
                        "Player 1", 20000101,
                        "  ", 20010101,
                        PlayerGameStart.FIRST_PLAYER));
    }

    @Test
    void playerGameRejectsDuplicateUsernames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startPlayerGame(
                        "Player", 20000101,
                        "Player", 20010101,
                        PlayerGameStart.FIRST_PLAYER));
    }

    @Test
    void finishingGameRecordsWinnerAndStatusTogether() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        game.finish("Player 2");

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player 2", game.getWinner().orElseThrow());
    }

    @Test
    void finishedGameKeepsOriginalWinnerWhenFinishIsCalledAgain() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);
        game.finish("Player 1");

        assertThrows(IllegalStateException.class, () -> game.finish("Player 2"));

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player 1", game.getWinner().orElseThrow());
    }

    @Test
    void finishingGameRejectsUnknownWinnerWithoutChangingLifecycle() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER);

        assertThrows(IllegalArgumentException.class, () -> game.finish("Unknown"));

        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void gameCannotFinishBeforeItStarts() {
        assertThrows(IllegalStateException.class, () -> game.finish("Player"));

        assertEquals(GameStatus.STARTING, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void birthdayTieStillSelectsExactlyOneStartingPlayer() throws Exception {
        game = new Game(new Random() {
            @Override
            public boolean nextBoolean() {
                return true;
            }
        });

        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20000101,
                PlayerGameStart.YOUNGER);

        assertNotEquals(game.getFirstPlayer().getIsTurn(), game.getSecondPlayer().getIsTurn());
        assertTrue(game.getFirstPlayer().getIsTurn());
    }
}
