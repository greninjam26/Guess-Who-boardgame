package com.guesswho.ui;

import com.guesswho.game.ComputerGameStart;
import com.guesswho.game.Game;
import com.guesswho.game.PlayerGameStart;

/**
 * Drives a {@link Game} from the choices the interface has collected.
 *
 * <p>The interface says what the player intends; this decides what that means
 * for the game. Keeping the translation here is what lets the setup screen
 * offer one list of opening-turn buttons rather than two mode-specific ones.</p>
 */
class GameController {
    private Game game;
    private final GameSetup setup;
    private OpeningTurn openingTurn;

    GameController(Game game, GameSetup setup) {
        this.game = game;
        this.setup = setup;
    }

    /**
     * Returns the game being driven.
     *
     * @return the underlying game
     */
    Game game() {
        return game;
    }

    /**
     * Returns the choices the setup screens collected.
     *
     * @return the game setup
     */
    GameSetup setup() {
        return setup;
    }

    /**
     * Starts the configured game with the chosen opening turn.
     *
     * @param openingTurn who takes the first turn
     * @throws IllegalArgumentException if the choice cannot apply to the
     *         configured mode, such as the computer starting a two-player game
     * @throws Exception if the board resources cannot be loaded
     */
    void start(OpeningTurn openingTurn) throws Exception {
        this.openingTurn = openingTurn;
        if (setup.isAgainstComputer()) {
            game.startComputerGame(
                    setup.firstUsername(),
                    setup.difficulty(),
                    computerStart(openingTurn),
                    setup.questionMode());
            return;
        }
        game.startPlayerGame(
                setup.firstUsername(),
                setup.firstBirthday(),
                setup.secondUsername(),
                setup.secondBirthday(),
                playerStart(openingTurn),
                setup.questionMode());
    }

    /**
     * Starts a fresh game between the same people, in the same mode, with the
     * same opening turn. Characters are not carried over — they are chosen
     * again, which is the point of playing another round.
     *
     * @throws IllegalStateException if no game has been started yet
     * @throws Exception if the board resources cannot be loaded
     */
    void rematch() throws Exception {
        if (openingTurn == null) {
            throw new IllegalStateException("No game has been started to replay");
        }
        game = new Game();
        start(openingTurn);
    }

    private ComputerGameStart computerStart(OpeningTurn openingTurn) {
        return switch (openingTurn) {
            case COMPUTER -> ComputerGameStart.COMPUTER;
            case FIRST_PLAYER -> ComputerGameStart.PLAYER;
            case RANDOM -> ComputerGameStart.RANDOM;
            case SECOND_PLAYER, YOUNGER -> throw new IllegalArgumentException(
                    "There is no second player in a game against the computer: " + openingTurn);
        };
    }

    private PlayerGameStart playerStart(OpeningTurn openingTurn) {
        return switch (openingTurn) {
            case FIRST_PLAYER -> PlayerGameStart.FIRST_PLAYER;
            case SECOND_PLAYER -> PlayerGameStart.SECOND_PLAYER;
            case RANDOM -> PlayerGameStart.RANDOM;
            case YOUNGER -> PlayerGameStart.YOUNGER;
            case COMPUTER -> throw new IllegalArgumentException(
                    "There is no computer opponent in a two-player game: " + openingTurn);
        };
    }
}
