import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameTest {
    private Game game;

    @BeforeEach
    void createGame() {
        game = new Game();
    }

    @Test
    void playerCanStartBeforeComputer() throws Exception {
        game.playerVsComputerPlayerFirst("PVCeasy", "Player");

        assertTrue(game.getUser1().getIsTurn());
        assertFalse(game.getAI().getIsTurn());
    }

    @Test
    void computerCanStartBeforePlayer() throws Exception {
        game.playerVsComputerAIFirst("PVChard", "Player");

        assertFalse(game.getUser1().getIsTurn());
        assertTrue(game.getAI().getIsTurn());
    }

    @Test
    void youngerPlayerStartsWhenBirthdayDeterminesTurn() throws Exception {
        game.playerVsPlayerBirthday("Younger", 20050101, "Older", 19950101);

        assertTrue(game.getUser1().getIsTurn());
        assertFalse(game.getUser2().getIsTurn());
    }

    @Test
    void birthdayTieStillSelectsExactlyOneStartingPlayer() throws Exception {
        game.playerVsPlayerBirthday("Player 1", 20000101, "Player 2", 20000101);

        assertNotEquals(game.getUser1().getIsTurn(), game.getUser2().getIsTurn());
    }
}
