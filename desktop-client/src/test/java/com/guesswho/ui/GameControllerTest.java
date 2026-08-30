package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.Game;
import com.guesswho.game.GameStatus;
import com.guesswho.game.QuestionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GameControllerTest {
    @Test
    void startsAComputerGameWithTheComputerMovingFirst() throws Exception {
        GameController controller = computerGame();

        controller.start(OpeningTurn.COMPUTER);

        assertEquals(GameStatus.IN_PROGRESS, controller.game().getStatus());
        assertEquals("AI", controller.game().getCurrentPlayerName());
    }

    @Test
    void startsAComputerGameWithThePlayerMovingFirst() throws Exception {
        GameController controller = computerGame();

        controller.start(OpeningTurn.FIRST_PLAYER);

        assertEquals("Alex", controller.game().getCurrentPlayerName());
    }

    @Test
    void startsATwoPlayerGameWithTheChosenPlayerMovingFirst() throws Exception {
        GameController controller = playerGame();

        controller.start(OpeningTurn.SECOND_PLAYER);

        assertEquals("Blake", controller.game().getCurrentPlayerName());
    }

    @Test
    void startsATwoPlayerGameWithTheYoungerPlayerMovingFirst() throws Exception {
        GameController controller = playerGame();

        controller.start(OpeningTurn.YOUNGER);

        assertEquals("Blake", controller.game().getCurrentPlayerName(),
                "The later birthday is the younger player");
    }

    @Test
    void rejectsASecondPlayerStartingAGameAgainstTheComputer() {
        GameController controller = computerGame();

        assertThrows(IllegalArgumentException.class,
                () -> controller.start(OpeningTurn.SECOND_PLAYER));
    }

    @Test
    void rejectsTheComputerStartingATwoPlayerGame() {
        GameController controller = playerGame();

        assertThrows(IllegalArgumentException.class,
                () -> controller.start(OpeningTurn.COMPUTER));
    }

    @Test
    void carriesTheSetupChoicesIntoTheRecordedResult() throws Exception {
        GameController controller = playerGame();
        controller.start(OpeningTurn.FIRST_PLAYER);
        controller.game().selectCharacter("Alex", "Sam");
        controller.game().selectCharacter("Blake", "Olivia");

        controller.game().finish("Alex");

        assertEquals(QuestionMode.FREE_FORM, controller.game().getGameResult().questionMode());
        assertNotEquals(null, controller.game().getGameResult().mode());
    }

    @Test
    void replaysWithTheSamePeopleAndANewGame() throws Exception {
        GameController controller = playerGame();
        controller.start(OpeningTurn.SECOND_PLAYER);
        controller.game().selectCharacter("Alex", "Sam");
        Game finished = controller.game();
        finished.finish("Alex");

        controller.rematch();

        assertNotSame(finished, controller.game(), "A finished game cannot be played again");
        assertEquals(GameStatus.IN_PROGRESS, controller.game().getStatus());
        assertEquals("Blake", controller.game().getCurrentPlayerName(),
                "The opening turn carries over");
        assertFalse(controller.game().hasSelectedCharacter("Alex"),
                "Characters are chosen again, which is the point of another round");
    }

    @Test
    void refusesToReplayAGameThatNeverStarted() {
        assertThrows(IllegalStateException.class, () -> playerGame().rematch());
    }

    private GameController computerGame() {
        GameSetup setup = new GameSetup();
        setup.againstComputer(ComputerDifficulty.HARD);
        setup.firstUsername("Alex");
        return new GameController(new Game(), setup);
    }

    private GameController playerGame() {
        GameSetup setup = new GameSetup();
        setup.againstPlayer(QuestionMode.FREE_FORM);
        setup.firstUsername("Alex");
        setup.firstBirthday(20000101);
        setup.secondUsername("Blake");
        setup.secondBirthday(20010101);
        return new GameController(new Game(), setup);
    }
}
