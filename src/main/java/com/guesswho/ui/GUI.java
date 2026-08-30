package com.guesswho.ui;

import com.guesswho.client.GameResultSubmissionService;
import com.guesswho.client.HttpGameResultClient;
import com.guesswho.client.HttpLeaderboardClient;
import com.guesswho.client.FilePendingGameResultStore;
import com.guesswho.client.LeaderboardClient;
import com.guesswho.game.Game;
import com.guesswho.game.GameResources;
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
import java.util.Optional;
import javax.sound.sampled.Clip;
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
    private static Optional<Clip> music = Optional.empty();
    //the list of characters image
    //image for the characters that were elimated
    //the size of the image
    //the veriables needed for the GUI to work
    private CharacterBoard boardPanel1;//first user's board
    private CharacterBoard boardPanel2;//second user's or the AI's board
    private CharacterBoard guessBoardPanel;
    //portraits shared by all three boards
    private CharacterImages images;
    private JPanel stepPanel;
    private JComboBox<String> stepInput;
    private JButton stepChoiceButton;
    private String choice;
    private JLabel stepLabel;
    private JButton questionChoiceButton;
    private String newQuestion;
    private JComboBox<String> questionComboBox;
    private JTextField questionTextField;
    private JLabel result1;
    private JButton guessButton;
    private JComboBox<String> guessComboBox;
    private String finalGuess;
    private JComboBox<String> questionAnswerComboBox;
    private JButton questionAnswerButton;
    private String questionAnswer;
    private String whosFirst;
    private Question AIQuestion;
    //true while the question panel is on screen, so it can be torn down again
    private boolean questionPanelShowing;
    private JPanel characterSelectionPanel;
    //welcome, mode, names, birthdays, and who goes first
    private SetupScreens setupScreens;
    //character reveal and answer review once the game is over
    private EndingScreens endingScreens;
    //the running transcript shown either side of the board
    private QuestionHistory history;
    private JButton askButton;
    private JButton guess;
    private JButton next;
    /**
     * Creates and displays the game interface.
     */
    public GUI() {
        images = new CharacterImages();
        gameGUI();
    }
    private void gameGUI() {
        frame = new JFrame("Guess Who? Game");//name of the frame
        frame.setPreferredSize(new Dimension(1350, 1200));// Width: 700 pixels, Height: 900 pixels
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
        JPanel controlPanel = new JPanel();
        JButton quitButton = new JButton("Quit");
        JButton restartButton = new JButton("Restart");
        JButton leaderboardButton = new JButton("Leaderboard");
        controlPanel.add(quitButton);
        controlPanel.add(restartButton);
        controlPanel.add(leaderboardButton);
        boardPanel1 = CharacterBoard.tracking(images);
        boardPanel2 = CharacterBoard.tracking(images);
        guessBoardPanel = CharacterBoard.selecting(images, characterIndex -> {
            frame.remove(guessBoardPanel);
            guessPVP(controller.game().getCurrentPlayerName(), characterIndex);
        });
        characterSelectionPanel = new JPanel();
        JLabel characterSelectionLabel = new JLabel("<html>Please select a character and remember it, cause in game it will not "
                + "be displaced. <br>Please click the ready button to start the game when you finish selecting your character.</html>");
        JButton readyButton = new JButton("Ready");
        characterSelectionPanel.add(characterSelectionLabel);
        characterSelectionPanel.add(readyButton);

        //stepPanel is used in each turn the user ask questions,
        //enter guess and choice what is their next step, wether to ask a question or make a guess
        stepPanel = new JPanel(null);
        stepLabel = new JLabel("Please make your choice: 1. ask question. 2. guess the character");
        stepLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
        stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
        String[] stepChoice = {"1", "2"};
        stepInput = new JComboBox<>(stepChoice);
        stepInput.setBounds(640, 675, 75, 30); // x, y, width, height
        stepChoiceButton = new JButton("Comfirm");
        stepChoiceButton.setBounds(715, 675, 100, 30); // x, y, width, height
        questionChoiceButton = new JButton("Comfirm");
        questionChoiceButton.setBounds(790, 675, 100, 30); // x, y, width, height
        guessButton = new JButton("Guess");
        guessButton.setBounds(715, 675, 100, 30); // x, y, width, height
        result1 = new JLabel("");
        result1.setBounds(0, 705, 1350, 30); // x, y, width, height
        result1.setHorizontalAlignment(SwingConstants.CENTER); // Center the label text
        String[] questionChoices = {"yes", "no"};
        questionAnswerComboBox = new JComboBox<String>(questionChoices);
        questionAnswerComboBox.setBounds(640, 675, 75, 30); // x, y, width, height
        questionAnswerButton = new JButton("Confirm");
        questionAnswerButton.setBounds(715, 675, 100, 30); // x, y, width, height
        JButton nextTurnButton = new JButton("Next Turn");
        nextTurnButton.setBounds(890, 705, 100, 30); // x, y, width, height
        stepPanel.add(stepLabel);
        stepPanel.add(result1);
        stepPanel.add(nextTurnButton);
        nextTurnButton.setVisible(false);

        history = new QuestionHistory();
        //this panel is used to display the ending massages
        //this panel is used to leftthe first player to enter their selected character
        //this the for the second player to enter the selected character
        //ask question button for PVP
        askButton = new JButton("ask question");
        askButton.setBounds(790, 675, 150, 30); // x, y, width, height
        //next turn button for PVP
        next = new JButton("next");
        next.setBounds(940, 675, 75, 30); // x, y, width, height
        //guess button for PVP
        guess = new JButton("guess");
        guess.setBounds(1015, 675, 100, 30); // x, y, width, height

        // Add start panel to the frame
        frame.add(setupScreens.panel(), BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.NORTH);
        // Show the frame
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        //this action listener is used to dispose the frame and stop the music and entire program
        quitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();//close the frame
                music.ifPresent(Clip::close);//stop the music
            }
        });
        //this action listener is used to dispose the frame and stop the music and restart entire program
        restartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();//close the frame
                gameGUI();//make a new one
            }
        });
        leaderboardButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LeaderboardDialog.show(frame, leaderboardClient);
            }
        });
        //action listener for the ready button when the user is ready to start play the game
        readyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.remove(characterSelectionPanel);
                if (controller.setup().isAgainstPlayer()) {//when it is PVP mode
                    if (controller.setup().isFreeFormQuestions()) {//if the user want to ask whatever questions they want during the game
                        //ask
                        freeAsk();
                    }
                    else {//when the player want to use the predefined questions
                        p2pGamePreQuestion();
                    }
                    //add in the panel and its components for game play
                    frame.add(stepPanel);
                    stepPanel.add(askButton);
                    stepPanel.add(next);
                    stepPanel.add(guess);
                    refreshFrame();
                }
                else {//when the user is play with the AI
                    //start the game
                    frame.add(boardPanel1);
                    frame.add(stepPanel);
                    refreshFrame();
                    oneTurn();//call the method to start the first turn
                }
            }
        });
        //action listener for when the user is finished choosing they step, whether they want to ask question or make a guess against the AI
        stepChoiceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                choice = (String) stepInput.getSelectedItem();
                stepPanel.remove(stepInput);
                stepPanel.remove(stepChoiceButton);
                if (choice.equals("1")) {//if user want to ask question
                    stepLabel.setText("Please choice the question you want to ask: ");
                    stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height of the stepLabel
                    //set up the question comboBox for the user with the question the user haven't asked
                    String[] questions = controller.game().getCurrentPlayerQuestionTexts();
                    questionComboBox = new JComboBox<String>(questions);
                    questionComboBox.setBounds(490, 675, 300, 30); // x, y, width, height
                    //add the questionsComboBox and questionChoiceButton
                    stepPanel.add(questionComboBox);
                    stepPanel.add(questionChoiceButton);
                    refreshFrame();
                }
                else {//if user want to make a guess
                    stepLabel.setText(controller.game().getCurrentPlayerName() + ", please enter your guess: ");
                    stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
                    //set up a guessComboBox to store all the possible characters that the user can guess
                    String[] characters = controller.game().getCharacterNames();
                    guessComboBox = new JComboBox<String>(characters);
                    guessComboBox.setBounds(565, 675, 150, 30); // x, y, width, height
                    stepPanel.add(guessComboBox);
                    stepPanel.add(guessButton);
                    refreshFrame();
                }
            }
        });
        //action listener when the user finish choosing the question to ask the AI
        questionChoiceButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                newQuestion = (String) questionComboBox.getSelectedItem();
                String AIAnswer = controller.game().askComputer(newQuestion);//store the AI's answer to user's question
                result1.setText("AI: " + AIAnswer);
                result1.setVisible(true);
                history.recordForFirst(
                        LabelText.escaped(newQuestion) + " : " + AIAnswer);
                stepPanel.remove(questionComboBox);
                stepPanel.remove(questionChoiceButton);
                nextTurnButton.setVisible(true);//add in the nextTurn button for the user to move on to the next turn
                //have space of waiting period
                //skip next turn button
                questionPanelShowing = true;
            }
        });
        //action listener for  when user want to go to the next turn
        nextTurnButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (questionPanelShowing) {//if the player just asked a question remove the questions comboBox and button
                    stepPanel.remove(questionComboBox);
                    stepPanel.remove(questionChoiceButton);
                }
                nextTurnButton.setVisible(false);
                result1.setVisible(false);
                oneTurn();//run next turn
            }
        });
        //action listener for the the button when user finish answering the AI's question
        questionAnswerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                questionAnswer = (String) questionAnswerComboBox.getSelectedItem();//read the question answer
                controller.game().answerComputerQuestion(questionAnswer.equals("yes"));
                history.recordForSecond(
                        LabelText.escaped(AIQuestion.getQuestion())
                                + " : " + questionAnswer);
                stepPanel.remove(questionAnswerButton);
                stepPanel.remove(questionAnswerComboBox);
                nextTurnButton.setVisible(true);//add in the nextTurn button
            }
        });
        //action listener for when the player is finished selected their guess
        guessButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                finalGuess = (String) guessComboBox.getSelectedItem();//get the guess
                String outcome = controller.game().guessComputer(finalGuess);
                frame.remove(boardPanel1);
                frame.remove(stepPanel);
                showEnding(outcome);
            }
        });
        //action listener for when the user is asking each other question
        askButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!controller.setup().isFreeFormQuestions()) {//if it is preset question user questionComboBox
                    newQuestion = questionComboBox.getSelectedItem().toString();
                }
                else {//use the textField
                    newQuestion = questionTextField.getText();
                    questionTextField.setText("");
                }
                String question = controller.game().getCurrentPlayerName() + ", " + newQuestion;//get the question
                //pop up window to ask the user question
                int result = JOptionPane.showConfirmDialog(null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
                controller.game().recordPlayerQuestion(controller.game().getCurrentPlayerName(), newQuestion, result == JOptionPane.YES_OPTION);
                String answer = result == JOptionPane.YES_OPTION ? "yes" : "no";
                String entry = LabelText.escaped(newQuestion) + "  " + answer + ".";
                if (controller.game().getCurrentPlayerName()
                        .equals(controller.setup().firstUsername())) {
                    history.recordForFirst(entry);
                }
                else {
                    history.recordForSecond(entry);
                }
                result1.setText(answer);
                askButton.setEnabled(false);
            }
        });
        //action listener the user use to guess the otherplayed character in pvp mode
        guess.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (controller.game().getCurrentPlayerName().equals(controller.setup().firstUsername())) {//user1's turn
                    frame.remove(boardPanel1);
                }
                else {//user2's turn
                    frame.remove(boardPanel2);
                }
                frame.remove(stepPanel);
                frame.add(guessBoardPanel);
                refreshFrame();
            }
        });
        //action listener the button the users use to switch turn in pvp modes
        next.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (controller.game().getCurrentPlayerName().equals(controller.setup().firstUsername())) {//when it is userboad's board
                    //remove the board1 and board2 need to remove and add the stepPanel for it to work
                    frame.remove(boardPanel1);
                    frame.remove(stepPanel);
                    frame.add(boardPanel2);
                    frame.add(stepPanel);
                }
                else {//when it is userboad's board
                    //remove the board2 and board1 need to remove and add the stepPanel for it to work
                    frame.remove(boardPanel2);
                    frame.remove(stepPanel);
                    frame.add(boardPanel1);
                    frame.add(stepPanel);
                }
                controller.game().advanceTurn();
                if (!controller.setup().isFreeFormQuestions()) {
                    questionComboBox.setModel(new DefaultComboBoxModel<String>(
                            controller.game().getCurrentPlayerQuestionTexts()));
                }
                stepLabel.setText(controller.game().getCurrentPlayerName() + ", Choose to ask a question or guess the answer: ");
                result1.setText("");
                askButton.setEnabled(true);
                refreshFrame();
            }
        });
    }
    /**
     * this method will run the code for one turn of the game, which could be player's turn or AI's.
     */
    private void oneTurn() {
        if (controller.game().getCurrentPlayerName().equals(controller.setup().firstUsername())) {//player turn
            stepLabel.setText("Please make your choice: 1. ask question. 2. guess the character");
            stepPanel.add(stepInput);
            stepPanel.add(stepChoiceButton);
            refreshFrame();
        }
        else {//AI turn
            Optional<String> computerGuess = controller.game().getComputerGuessName();
            if (computerGuess.isPresent()) {//if there is only one possible character left
                String question = "Is " + computerGuess.orElseThrow() + " the character? ";
                int result = JOptionPane.showConfirmDialog(
                        null, question, "Confirmation", JOptionPane.YES_NO_OPTION);
                boolean correct = result == JOptionPane.YES_OPTION;
                String winner = controller.game().resolveComputerGuess(correct);
                String ans;
                if (winner.equals(controller.setup().firstUsername())) {//the AI guessed the wrong character
                    ans = "Congraulation, " + controller.setup().firstUsername()
                            + ", you won!!!! Because the AI guessed the wrong character";
                }
                else {//the AI guessed the correct character
                    ans = "Sorry, " + controller.setup().firstUsername()
                            + " the AI guessed your character, you lost.";
                }
                JOptionPane.showMessageDialog(
                        null, ans, "Message", JOptionPane.INFORMATION_MESSAGE);
                //remove the board and the steps
                frame.remove(boardPanel1);
                frame.remove(stepPanel);
                showEnding(ans);
            }
            else {// there are morn than one possible characters
                AIQuestion = controller.game().playComputerQuestion();//get the question
                String choosenQuestion = AIQuestion.getQuestion();
                //displace the question
                stepLabel.setText(choosenQuestion);
                stepLabel.setBounds(390, 625, 600, 30); // x, y, width, height
                stepLabel.setVisible(true);
                stepPanel.add(questionAnswerButton);
                stepPanel.add(questionAnswerComboBox);
                refreshFrame();
            }
        }
    }
    /**
     * this method will be flipping or changing the character buttons when it is clicked
     * @param button the button that was clicked
     * @param iconStates the array what stores the state of the button
     * @param index the index of the button in the array
     */
    /**
     * repaint the frame
     */
    private void refreshFrame() {
        frame.revalidate();
        frame.repaint();
    }
    /**
     * this method will be called when the it pvp predefined question mode is starting, it add in all the boards and panels
     */
    private void p2pGamePreQuestion() {
        if (controller.game().getCurrentPlayerName().equals(controller.setup().firstUsername())) {
            frame.add(boardPanel1);
        }
        else {
            frame.add(boardPanel2);
        }

        stepLabel.setText(controller.game().getCurrentPlayerName() + ", Choose a question or guess the character");
        //creating the questionComboBox
        String[] questions = controller.game().getCurrentPlayerQuestionTexts();
        questionComboBox = new JComboBox<String>(questions);
        questionComboBox.setBounds(490, 675, 300, 30); // x, y, width, height
        //add the questionsComboBox
        stepPanel.add(questionComboBox);

        result1.setText("");
    }
    /**
     * this method will be run when the pvp ask question game is starting, it set up the board and question asking
     */
    private void freeAsk() {
        if (controller.game().getCurrentPlayerName().equals(controller.setup().firstUsername())) {
            frame.add(boardPanel1);
        }
        else {
            frame.add(boardPanel2);
        }

        stepLabel.setText(controller.game().getCurrentPlayerName() + ", input a question or guess the character (don't make the question go over 43 letters including space)");
//		stepLabel.setBounds(200, 900, 850, 60);
        questionTextField = new JTextField();//43 max
        questionTextField.setBounds(490, 675, 300, 30); // x, y, width, height
        //add the questionTextField
        stepPanel.add(questionTextField);

        result1.setText("");
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
        frame.add(characterSelectionPanel);
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
        //uploading the music
        music = GameResources.loadBackgroundMusic();
        music.ifPresent(clip -> {
            clip.start();
            clip.loop(Clip.LOOP_CONTINUOUSLY);//keep repeating the music
        });
        //run the GUI
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new GUI();
            }
        });
    }
}
