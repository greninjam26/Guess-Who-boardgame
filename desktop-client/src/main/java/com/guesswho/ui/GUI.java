package com.guesswho.ui;

import com.guesswho.client.GameResultSubmissionService;
import com.guesswho.client.HttpAccountClient;
import com.guesswho.client.HttpOnlineGameClient;
import com.guesswho.client.HttpGameResultClient;
import com.guesswho.client.HttpLeaderboardClient;
import com.formdev.flatlaf.FlatLightLaf;

import com.guesswho.client.AccountClient;
import com.guesswho.client.ApplicationDirectory;
import com.guesswho.client.FilePendingGameResultStore;
import com.guesswho.client.LeaderboardClient;
import com.guesswho.client.TokenStore;
import com.guesswho.game.Game;
import com.guesswho.game.GameStatus;

/*Author: Gavin Liu
 * Date: Jan 8 2024
 * Description: this class contains all the basic front end code that have all the buttons and panels working
 * but the styling should be improved on for it to look good.
 * */
import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/**
 * Swing user interface for configuring and playing Guess Who games.
 */
public class GUI {
    // Main frame of the application
    private JFrame frame;
    //new game class for the game
    //drives the game from the choices the setup screens collected
    private GameController controller;
    //submits results to the server, queueing them locally while it is unreachable
    private final GameResultSubmissionService resultSubmissionService =
            new GameResultSubmissionService(
                    new HttpGameResultClient(),
                    new FilePendingGameResultStore(ApplicationDirectory.forThisMachine()
                            .resolve("pending-game-results.jsonl")));
    //who is playing: a signed-in account, or a guest
    private final AccountClient accountClient = new HttpAccountClient();
    private PlayerIdentity identity;
    private SignInScreen signInScreen;
    private AccountControls accountControls;
    private boolean accountChoiceComplete;
    private boolean gameActive;
    //online play, which has its own controller and its own screens
    private OnlineGameController onlineController;
    private OnlineRoomScreen onlineRoomScreen;
    private OnlineGameScreens onlineScreens;
    //keeps the game in progress, so a closed window is not a lost game
    private final SavedGameStore savedGames = new SavedGameStore();
    //remembers the online room, so a closed window is not a lost game there either
    private final com.guesswho.client.ActiveRoomStore activeRoom =
            new com.guesswho.client.ActiveRoomStore();
    //retrieves server-backed leaderboard standings without blocking Swing
    private final LeaderboardClient leaderboardClient = new HttpLeaderboardClient();
    //the music
    private static BackgroundMusic music;
    //the list of characters image
    //image for the characters that were elimated
    //the size of the image
    //the veriables needed for the GUI to work
    private CharacterBoard boardPanel1;//first user's board
    private CharacterBoard boardPanel2;//second user's or the AI's board
    private CharacterBoard guessBoardPanel;
    //The guess board with its prompt above it, since the frame swaps one thing.
    private JPanel guessScreen;
    //portraits shared by all three boards
    private CharacterImages images;
    //true while the question panel is on screen, so it can be torn down again
    //where each player picks the character their opponent must guess
    private CharacterChoiceScreens characterChoice;
    //welcome, mode, names, birthdays, and who goes first
    private SetupScreens setupScreens;
    //character reveal and answer review once the game is over
    private EndingScreens endingScreens;
    //the running transcript shown either side of the board
    private QuestionHistory history;
    //turn controls for a game against the computer
    private ComputerTurnPanel computerTurns;
    //turn controls for two people sharing this machine
    private PlayerTurnPanel playerTurns;
    /**
     * Creates and displays the game interface.
     */
    public GUI() {
        images = new CharacterImages();
        frame = new JFrame("Guess Who? Game");//name of the frame
        //No fixed size: every screen now states what it needs and pack() honours it.
        frame.setMinimumSize(new Dimension(760, 520));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        identity = new PlayerIdentity(accountClient, new TokenStore());
        //Resuming is checked before the window is shown, so nobody types into
        //a sign-in screen that then vanishes.
        accountChoiceComplete = identity.resumePreviousSession().isPresent();
        buildGameInterface();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        //The online room first: it has a clock running and a room that expires,
        //where a saved local game waits as long as it likes. Only one is offered
        //on a launch — two questions before the menu is an interrogation.
        if (!offerOnlineRoom()) {
            offerSavedGame();
        }
    }

    /** Builds a fresh game session inside the existing application window. */
    private void buildGameInterface() {
        //inialization of some of the veriables
        GameSetup setup = new GameSetup();
        controller = new GameController(new Game(), setup);
        setupScreens = new SetupScreens(setup, this::showInputError, this::startGame,
                this::beginOnlineGame);
        signInScreen = new SignInScreen(accountClient, identity, this::beginSetup);
        endingScreens = new EndingScreens(controller, images,
                trustworthy -> {
                    if (trustworthy) {
                        submitGameResult();
                    }
                    refreshFrame();
                },
                this::playAgain,
                this::returnHome);
        //Account state stays visible on every screen. Changing it is enabled
        //only while setup is on screen, so an active game cannot lose its owner.
        JPanel controlPanel = new JPanel(new BorderLayout());
        UiTheme.styleToolbar(controlPanel);
        accountControls = new AccountControls(this::showAccountChoice, this::signOut);
        JButton settingsButton = new JButton("Settings");
        UiTheme.styleToolbarButton(settingsButton);
        controlPanel.add(accountControls, BorderLayout.WEST);
        controlPanel.add(settingsButton, BorderLayout.EAST);
        boardPanel1 = CharacterBoard.tracking(images, this::saveGame);
        boardPanel2 = CharacterBoard.tracking(images, this::saveGame);
        guessBoardPanel = CharacterBoard.selecting(images, characterIndex -> {
            frame.remove(guessScreen);
            guessPVP(controller.game().getCurrentPlayerName(), characterIndex);
        });
        guessScreen = new JPanel(new BorderLayout());
        guessScreen.add(GuessPrompt.label(), BorderLayout.NORTH);
        guessScreen.add(guessBoardPanel, BorderLayout.CENTER);
        

        history = new QuestionHistory();
        characterChoice = new CharacterChoiceScreens(controller, () -> {
            frame.remove(characterChoice.panel());
            if (controller.setup().isAgainstPlayer()) {
                playerTurns.beginTurn();//adds the board, then the controls over it
                return;
            }
            beginComputerPlay();
        });
        computerTurns = new ComputerTurnPanel(controller, history, outcome -> {
            frame.remove(boardPanel1);
            frame.remove(computerTurns);
            showEnding(outcome);
        });
        playerTurns = new PlayerTurnPanel(controller, history, new PlayerTurnPanel.Boards() {
            @Override
            public void showBoardForCurrentPlayer() {
                frame.remove(boardPanel1);
                frame.remove(boardPanel2);
                frame.add(currentPlayerBoard(), BorderLayout.CENTER);
                frame.add(playerTurns, BorderLayout.SOUTH);
                refreshFrame();
            }

            @Override
            public void showGuessBoard() {
                frame.remove(boardPanel1);
                frame.remove(boardPanel2);
                frame.remove(playerTurns);
                //Carried over from the board this player was just looking at, so
                //a game's worth of eliminating is still on screen at the moment
                //it decides the guess.
                guessBoardPanel.showRuledOut(currentPlayerBoard().faceDownCards());
                frame.add(guessScreen, BorderLayout.CENTER);
                refreshFrame();
            }
        });
        onlineController = new OnlineGameController(
                new HttpOnlineGameClient(),
                new RoomPoller(new HttpOnlineGameClient()),
                () -> identity.token().orElse(null));
        onlineRoomScreen = new OnlineRoomScreen(
                onlineController, this::showOnlineGame, this::leaveOnlinePlay);
        try {
            onlineScreens = new OnlineGameScreens(onlineController, images,
                    new com.guesswho.game.Board(), false, this::leaveOnlinePlay);
        }
        catch (Exception unloadable) {
            throw new IllegalStateException("The board could not be loaded", unloadable);
        }
        computerTurns.onTurnChange(this::saveGame);
        playerTurns.onTurnChange(this::saveGame);
        //this panel is used to display the ending massages
        //this panel is used to leftthe first player to enter their selected character
        //this the for the second player to enter the selected character

        accountControls.show(identity.username());
        accountControls.switchingAllowed(accountChoiceComplete);
        identity.username().ifPresent(controller.setup()::firstUsername);
        frame.add(accountChoiceComplete ? setupScreens.panel() : signInScreen.panel(),
                BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.NORTH);
        frame.pack();
        settingsButton.addActionListener(event -> SettingsDialog.show(
                frame,
                music,
                leaderboardClient,
                this::returnHome,
                () -> {
                    music.close();
                    frame.dispose();
                }));
    }
    /**
     * repaint the frame
     */
    private void refreshFrame() {
        frame.revalidate();
        frame.repaint();
    }
    /**
     * this method will have a pop up window to ask the other player wether the first player's guess is right or wrong
     * depend on the answer store the result in a label
     * add in the in panel that ask the users to input the selected character
     * @param guessingUsername the username of the user that is guessing
     * @param index the index of the character of the user1's guess
     */
    private void guessPVP(String guessingUsername, int index) {
        String[] characters = controller.game().getCharacterNames();//get the guessed character
        String question = "Is " + characters[index] + " the character? ";//question statement
        int result = JOptionPane.showConfirmDialog(null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
        String winningUsername = controller.game().resolvePlayerGuess(
                guessingUsername, characters[index], result == JOptionPane.YES_OPTION);
        String winner = LabelText.escaped(winningUsername);
        String guesser = LabelText.escaped(guessingUsername);
        String outcome = result == JOptionPane.YES_OPTION
                ? "<html>Congraulation, " + winner
                        + " you guessed the character, you won!!!!</html>"
                : "<html>Congraulation, " + winner + ", you won!!!! <br>Because "
                        + guesser + " you guessed the wrong character</html>";
        showEnding(outcome);
    }
    /**
     * this method will read the eliminated character icon and storing it
     */
    /**
     * this method will read the images and stored them and it will only be called once in the beginning of the program
     */

    private void showInputError(String message) {
        JOptionPane.showMessageDialog(
                frame, message, "Invalid game setup", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * this method starts the game with the chosen opening turn and moves on to
     * character selection, or reports why the game could not be started
     * @param openingTurn who the player chose to take the first turn
     */
    private void startGame(OpeningTurn openingTurn) {
        try {
            controller.start(openingTurn);
        }
        catch (Exception exception) {
            handleGameStartFailure(exception);
            return;
        }
        gameActive = true;
        accountControls.switchingAllowed(false);
        frame.remove(setupScreens.panel());
        history.begin(
                controller.setup().firstUsername(),
                controller.setup().isAgainstComputer()
                        ? "AI"
                        : controller.setup().secondUsername());
        frame.add(characterChoice.panel(), BorderLayout.CENTER);
        characterChoice.begin();
        refreshFrame();
    }
    private void handleGameStartFailure(Exception exception) {
        String message = exception.getMessage() == null
                ? "The game could not be started."
                : exception.getMessage();
        JOptionPane.showMessageDialog(
                frame, message, "Unable to start game", JOptionPane.ERROR_MESSAGE);
        refreshFrame();
    }

    /**
     * this method hands over to the ending screens, which ask each player which
     * character they were holding before revealing them
     * @param outcome the message describing who won and why
     */
    /**
     * this method starts another game between the same people, in the same mode.
     * everything the previous game left behind has to go: the cards each player
     * flipped, the transcripts down either side, and the panels showing how it
     * ended
     */
    private void playAgain() {
        savedGames.clear();
        try {
            controller.rematch();
        }
        catch (Exception exception) {
            handleGameStartFailure(exception);
            return;
        }
        gameActive = true;
        frame.remove(endingScreens.panel());
        frame.remove(history.firstPanel());
        frame.remove(history.secondPanel());
        boardPanel1.reset();
        boardPanel2.reset();
        guessBoardPanel.reset();
        history.begin(
                controller.setup().firstUsername(),
                controller.setup().isAgainstComputer()
                        ? "AI"
                        : controller.setup().secondUsername());
        frame.add(characterChoice.panel(), BorderLayout.CENTER);
        characterChoice.begin();
        refreshFrame();
    }

    private void showEnding(String outcome) {
        //The game is over, so there is nothing left to come back to.
        savedGames.clear();
        gameActive = false;
        frame.add(endingScreens.panel(), BorderLayout.CENTER);
        frame.add(history.firstPanel(), BorderLayout.EAST);
        frame.add(history.secondPanel(), BorderLayout.WEST);
        endingScreens.begin(outcome);
        refreshFrame();
    }
    /**
     * Moves on from the sign-in screen, whether they signed in or not.
     *
     * <p>A signed-in player has already given their name once, so the setup
     * screens do not ask again.</p>
     */
    private void beginSetup() {
        frame.remove(signInScreen.panel());
        accountChoiceComplete = true;
        controller.setup().firstUsername(identity.username().orElse(null));
        accountControls.show(identity.username());
        accountControls.switchingAllowed(true);
        frame.add(setupScreens.panel(), BorderLayout.CENTER);
        refreshFrame();
    }

    /** Opens a fresh account choice screen from setup. */
    private void showAccountChoice() {
        frame.remove(setupScreens.panel());
        accountChoiceComplete = false;
        signInScreen = new SignInScreen(accountClient, identity, this::beginSetup);
        accountControls.switchingAllowed(false);
        frame.add(signInScreen.panel(), BorderLayout.CENTER);
        refreshFrame();
    }

    /** Ends the session and returns to the account choice screen. */
    private void signOut() {
        identity.signOut();
        controller.setup().firstUsername(null);
        accountControls.show(identity.username());
        showAccountChoice();
    }

    /** Stops the current session and rebuilds home inside this same window. */
    private void returnHome() {
        HomeNavigation.returnHome(
                gameActive,
                this::confirmLeavingGame,
                this::stopCurrentGame,
                frame.getContentPane(),
                this::buildGameInterface);
    }

    private boolean confirmLeavingGame() {
        int answer = JOptionPane.showConfirmDialog(
                frame,
                "Leave the current game and return home?",
                "Return home",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    private void stopCurrentGame() {
        savedGames.clear();
        activeRoom.clear();
        if (onlineController != null) {
            onlineController.leave();
        }
        gameActive = false;
    }

    // --- online play ----------------------------------------------------

    /**
     * Leaves the setup screens for the online ones.
     *
     * <p>Signing in is required: an online game has to know who is on each
     * side, so a guest is sent back to sign in rather than into a room they
     * could not be attributed in.</p>
     */
    private void beginOnlineGame() {
        if (!identity.isSignedIn()) {
            showInputError("Sign in to play online. You can play the rest as a guest.");
            return;
        }
        gameActive = true;
        accountControls.switchingAllowed(false);
        frame.remove(setupScreens.panel());
        onlineRoomScreen.begin();
        frame.add(onlineRoomScreen.panel(), BorderLayout.CENTER);
        refreshFrame();
    }

    /** Hands over from the room screen to the board, once there is a game. */
    private void showOnlineGame(com.guesswho.room.RoomState state) {
        frame.remove(onlineRoomScreen.panel());
        //From here the board is what the server talks to, not the room screen.
        onlineController.showOn(onlineView());
        onlineScreens.show(state);
        frame.add(onlineScreens.panel(), BorderLayout.CENTER);
        refreshFrame();
    }

    /** Puts the online board on screen before there is a state to show on it. */
    private void showOnlineBoard() {
        onlineScreens.showRejoining();
        frame.add(onlineScreens.panel(), BorderLayout.CENTER);
        refreshFrame();
    }

    /** What the online controller reports to, however the game was entered. */
    private OnlineGameController.View onlineView() {
        return new OnlineGameController.View() {

            @Override
            public void roomOpened(com.guesswho.room.Room room) {
            }

            @Override
            public void stateChanged(com.guesswho.room.RoomState updated) {
                //Remembered from the state rather than from roomOpened, because
                //rejoining never calls that one — and a rejoined game is exactly
                //the one that must still be remembered next time.
                if (updated.status() == com.guesswho.room.RoomStatus.FINISHED) {
                    activeRoom.clear();
                    gameActive = false;
                }
                else {
                    activeRoom.save(updated.code());
                }
                onlineScreens.show(updated);
            }

            @Override
            public void problem(String message) {
                showInputError(message);
            }

            @Override
            public void connectionLost() {
                //A banner on the board, not a dialog. This used to be a modal
                //error every two seconds for as long as the network was down.
                onlineScreens.showConnectionTrouble(true);
            }

            @Override
            public void connectionRestored() {
                onlineScreens.showConnectionTrouble(false);
            }

            @Override
            public void revealed(com.guesswho.room.GameReveal reveal) {
                onlineScreens.showReveal(reveal);
            }

            @Override
            public void cannotContinue(String message) {
                //Nothing to come back to, so nothing to offer next launch.
                activeRoom.clear();
                gameActive = false;
                onlineScreens.showGone(message);
            }

            @Override
            public void signedOut() {
                identity.signOut();
                accountControls.show(identity.username());
                showInputError("You have been signed out. Sign in again to play online.");
                leaveOnlinePlay();
            }
        };
    }

    /** Back to the setup screens, with nothing left polling. */
    private void leaveOnlinePlay() {
        //Walking back to the menu is leaving the game, not pausing it. Keeping
        //the code would offer a room the player chose to step out of.
        activeRoom.clear();
        onlineController.leave();
        gameActive = false;
        frame.remove(onlineRoomScreen.panel());
        frame.remove(onlineScreens.panel());
        accountControls.switchingAllowed(true);
        frame.add(setupScreens.panel(), BorderLayout.CENTER);
        refreshFrame();
    }

    // --- saving and resuming ------------------------------------------

    private void beginComputerPlay() {
        frame.add(boardPanel1, BorderLayout.CENTER);
        frame.add(computerTurns, BorderLayout.SOUTH);
        refreshFrame();
        computerTurns.beginTurn();
    }

    /**
     * Keeps the game as it stands, after anything that changes it.
     *
     * <p>Called on every flipped card as well as every turn, because the cards
     * are the player's working notes and losing an evening of them would be
     * the thing they noticed.</p>
     */
    private void saveGame() {
        if (controller.game().getStatus() != GameStatus.IN_PROGRESS) {
            return;
        }
        savedGames.save(new SavedGame(
                SavedGame.VERSION,
                controller.game().snapshot(),
                controller.setup().tellsCharacterUpFront(),
                controller.openingTurn(),
                boardPanel1.faceDownCards(),
                boardPanel2.faceDownCards(),
                history.firstEntries(),
                history.secondEntries()));
    }

    /** Asks, on launch, whether to pick up where the last game left off. */
    /**
     * Offers to pick an online game back up, if this client was in one.
     *
     * <p>Whether the room is still there is deliberately not checked before
     * asking. That would be a request on every launch, on the chance that one
     * of them was in a game; a room that has gone is reported by the first poll
     * and lands on the game-gone screen, which is where that news belongs
     * anyway.</p>
     *
     * @return whether an offer was made, so a launch asks at most one question
     */
    private boolean offerOnlineRoom() {
        Optional<String> room = activeRoom.read();
        if (room.isEmpty()) {
            return false;
        }
        if (identity.token().isEmpty()) {
            //An online game needs the account that was in it. Without a session
            //there is nothing to offer, and the code would only be refused.
            activeRoom.clear();
            return false;
        }
        int answer = JOptionPane.showConfirmDialog(
                frame,
                "You were in an online game (room " + room.get() + "). Rejoin it?",
                "Rejoin game",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            //Declining is a decision, and leaving the code would ask again.
            activeRoom.clear();
            return true;
        }
        gameActive = true;
        accountControls.switchingAllowed(false);
        frame.remove(setupScreens.panel());
        showOnlineBoard();
        onlineController.rejoin(room.get(), onlineView());
        return true;
    }

    private void offerSavedGame() {
        Optional<SavedGame> saved = savedGames.read();
        if (saved.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                frame,
                "You have a game in progress. Carry on with it?",
                "Resume game",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            resume(saved.get());
            return;
        }
        //Declining is a decision, and leaving the file would ask again next time.
        savedGames.clear();
    }

    private void resume(SavedGame saved) {
        try {
            saved.restoreSetup(controller.setup());
            controller.resume(Game.restoredFrom(saved.game()), saved.openingTurn());
        }
        catch (Exception exception) {
            //The save is the only thing that failed. Drop it and let them play.
            savedGames.clear();
            handleGameStartFailure(exception);
            return;
        }
        boardPanel1.restore(saved.firstBoard());
        boardPanel2.restore(saved.secondBoard());
        history.restore(saved.firstTranscript(), saved.secondTranscript());

        gameActive = true;
        accountControls.switchingAllowed(false);
        frame.remove(setupScreens.panel());
        if (controller.setup().isAgainstPlayer()) {
            playerTurns.beginTurn();
        }
        else {
            beginComputerPlay();
        }
    }

    /** The tracking board belonging to whoever's turn it is. */
    private CharacterBoard currentPlayerBoard() {
        return controller.game().getCurrentPlayerName()
                .equals(controller.setup().firstUsername())
                        ? boardPanel1
                        : boardPanel2;
    }

    private void submitGameResult() {
        //Signed in, and the server attributes the game to that account rather
        //than to whatever name was typed. A guest sends no token and their
        //result is still stored, just not owned by anybody.
        resultSubmissionService.submit(
                        controller.game().getGameResult(), identity.token().orElse(null))
                .exceptionally(failure -> {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            frame,
                            "The game result could not be stored.",
                            "Unable to store result",
                            JOptionPane.ERROR_MESSAGE));
                    return null;
                });
    }

    /**
     * Starts background music when available and launches the Swing interface.
     *
     * @param args command-line arguments; currently unused
     */
    public static void main(String[] args) {
        //Before any component exists, or half the interface keeps the old look.
        //Swing's default look and feel is decades old; this is the same flat
        //theming IntelliJ uses, and it brings HiDPI handling with it.
        FlatLightLaf.setup();
        //uploading the music
        music = new BackgroundMusic();
        music.start();
        //run the GUI
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new GUI();
            }
        });
    }
}
