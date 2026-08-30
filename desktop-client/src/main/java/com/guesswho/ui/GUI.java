package com.guesswho.ui;

import com.guesswho.client.GameResultSubmissionService;
import com.guesswho.client.HttpGameResultClient;
import com.guesswho.client.HttpLeaderboardClient;
import com.formdev.flatlaf.FlatLightLaf;

import com.guesswho.client.FilePendingGameResultStore;
import com.guesswho.client.LeaderboardClient;
import com.guesswho.game.Game;
import com.guesswho.game.Question;

/*Author: Gavin Liu
 * Date: Jan 8 2024
 * Description: this class contains all the basic front end code that have all the buttons and panels working
 * but the styling should be improved on for it to look good.
 * */
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.border.Border;

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
                    new FilePendingGameResultStore("pending-game-results.jsonl"));
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
    //portraits shared by all three boards
    private CharacterImages images;
    private JComboBox<String> guessComboBox;
    //true while the question panel is on screen, so it can be torn down again
    private boolean questionPanelShowing;
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
        gameGUI();
    }
    private void gameGUI() {
        frame = new JFrame("Guess Who? Game");//name of the frame
        //No fixed size: every screen now states what it needs and pack() honours it.
        frame.setMinimumSize(new Dimension(760, 520));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        //inialization of some of the veriables
        GameSetup setup = new GameSetup();
        controller = new GameController(new Game(), setup);
        setupScreens = new SetupScreens(setup, this::showInputError, this::startGame);
        endingScreens = new EndingScreens(controller, images, trustworthy -> {
            if (trustworthy) {
                submitGameResult();
            }
            refreshFrame();
        });
        questionPanelShowing = false;
        //One button, rather than three across the top of every screen.
        JPanel controlPanel = new JPanel();
        JButton settingsButton = new JButton("Settings");
        controlPanel.add(settingsButton);
        boardPanel1 = CharacterBoard.tracking(images);
        boardPanel2 = CharacterBoard.tracking(images);
        guessBoardPanel = CharacterBoard.selecting(images, characterIndex -> {
            frame.remove(guessBoardPanel);
            guessPVP(controller.game().getCurrentPlayerName(), characterIndex);
        });
        

        history = new QuestionHistory();
        characterChoice = new CharacterChoiceScreens(controller, () -> {
            frame.remove(characterChoice.panel());
            if (controller.setup().isAgainstPlayer()) {
                playerTurns.beginTurn();//adds the board, then the controls over it
                return;
            }
            frame.add(boardPanel1, BorderLayout.CENTER);
            frame.add(computerTurns, BorderLayout.SOUTH);
            refreshFrame();
            computerTurns.beginTurn();
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
                frame.add(controller.game().getCurrentPlayerName()
                        .equals(controller.setup().firstUsername())
                                ? boardPanel1
                                : boardPanel2, BorderLayout.CENTER);
                frame.add(playerTurns, BorderLayout.SOUTH);
                refreshFrame();
            }

            @Override
            public void showGuessBoard() {
                frame.remove(boardPanel1);
                frame.remove(boardPanel2);
                frame.remove(playerTurns);
                frame.add(guessBoardPanel, BorderLayout.CENTER);
                refreshFrame();
            }
        });
        //this panel is used to display the ending massages
        //this panel is used to leftthe first player to enter their selected character
        //this the for the second player to enter the selected character

        // Add start panel to the frame
        frame.add(setupScreens.panel(), BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.NORTH);
        // Show the frame
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        settingsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SettingsDialog.show(frame, music, leaderboardClient,
                        () -> {
                            frame.dispose();
                            gameGUI();
                        },
                        () -> {
                            music.close();
                            frame.dispose();
                        });
            }
        });
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
    private void showEnding(String outcome) {
        frame.add(endingScreens.panel(), BorderLayout.CENTER);
        frame.add(history.firstPanel(), BorderLayout.EAST);
        frame.add(history.secondPanel(), BorderLayout.WEST);
        endingScreens.begin(outcome);
        refreshFrame();
    }
    private void submitGameResult() {
        resultSubmissionService.submit(controller.game().getGameResult())
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
