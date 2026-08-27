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
