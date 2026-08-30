package com.guesswho.ui;

import com.guesswho.game.Question;

import java.util.Optional;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * The turn controls for a game against the computer.
 *
 * <p>A turn is one of four states, and only one set of controls is on screen at
 * a time: choosing whether to ask or guess, picking a question, picking a
 * character to guess, or answering the computer's question. They used to share
 * one panel and be added and removed by hand from six listeners, which is why
 * a stale control occasionally had to be removed defensively.</p>
 */
class ComputerTurnPanel extends JPanel {
    /** Notified when the game is over. */
    @FunctionalInterface
    interface GameOver {
        /**
         * Called once the game has been resolved.
         *
         * @param outcome message describing who won and why
         */
        void gameFinished(String outcome);
    }

    private final GameController controller;
    private final QuestionHistory history;
    private final GameOver gameOver;

    private final JLabel prompt = new JLabel();
    private final JLabel answerShown = new JLabel("");
    private final JComboBox<String> askOrGuess = new JComboBox<>(new String[] {"1", "2"});
    private final JButton confirmChoice = new JButton("Comfirm");
    private final JComboBox<String> questions = new JComboBox<>();
    private final JButton confirmQuestion = new JButton("Comfirm");
    private final JComboBox<String> characters = new JComboBox<>();
    private final JButton confirmGuess = new JButton("Guess");
    private final JComboBox<String> yesOrNo = new JComboBox<>(new String[] {"yes", "no"});
    private final JButton confirmAnswer = new JButton("Confirm");
    private final JButton nextTurn = new JButton("Next Turn");

    /**
     * Builds the controls.
     *
     * @param controller the game in progress
     * @param history transcript to record questions and answers in
     * @param gameOver notified once the game is resolved
     */
    ComputerTurnPanel(GameController controller, QuestionHistory history, GameOver gameOver) {
        super(null);
        this.controller = controller;
        this.history = history;
        this.gameOver = gameOver;

        prompt.setHorizontalAlignment(SwingConstants.CENTER);
        prompt.setBounds(390, 625, 600, 30);
        answerShown.setHorizontalAlignment(SwingConstants.CENTER);
        answerShown.setBounds(0, 705, 1350, 30);
        askOrGuess.setBounds(640, 675, 75, 30);
        confirmChoice.setBounds(715, 675, 100, 30);
        questions.setBounds(490, 675, 300, 30);
        confirmQuestion.setBounds(790, 675, 100, 30);
        characters.setBounds(565, 675, 150, 30);
        confirmGuess.setBounds(715, 675, 100, 30);
        yesOrNo.setBounds(640, 675, 75, 30);
        confirmAnswer.setBounds(715, 675, 100, 30);
        nextTurn.setBounds(890, 705, 100, 30);

        add(prompt);
        add(answerShown);
        for (java.awt.Component control : new java.awt.Component[] {
                askOrGuess, confirmChoice, questions, confirmQuestion,
                characters, confirmGuess, yesOrNo, confirmAnswer, nextTurn}) {
            control.setVisible(false);
            add(control);
        }

        confirmChoice.addActionListener(event -> chooseAskOrGuess());
        confirmQuestion.addActionListener(event -> askChosenQuestion());
        confirmGuess.addActionListener(event -> guessChosenCharacter());
        confirmAnswer.addActionListener(event -> answerComputer());
        nextTurn.addActionListener(event -> beginTurn());
    }

    /**
     * Runs one turn, which belongs either to the player or to the computer.
     */
    void beginTurn() {
        showOnly();
        answerShown.setText("");
        if (controller.game().getCurrentPlayerName()
                .equals(controller.setup().firstUsername())) {
            prompt.setText("Please make your choice: 1. ask question. 2. guess the character");
            showOnly(askOrGuess, confirmChoice);
            return;
        }
        computerTurn();
    }

    private void computerTurn() {
        Optional<String> readyToGuess = controller.game().getComputerGuessName();
        if (readyToGuess.isPresent()) {
            resolveComputerGuess(readyToGuess.orElseThrow());
            return;
        }
        Question asked = controller.game().playComputerQuestion();
        prompt.setText(asked.getQuestion());
        showOnly(yesOrNo, confirmAnswer);
    }

    private void resolveComputerGuess(String guessedCharacter) {
        int reply = JOptionPane.showConfirmDialog(
                null, "Is " + guessedCharacter + " the character? ",
                "Confirmation", JOptionPane.YES_NO_OPTION);
        String winner = controller.game().resolveComputerGuess(reply == JOptionPane.YES_OPTION);
        String player = controller.setup().firstUsername();
        String outcome = winner.equals(player)
                ? "Congraulation, " + player
                        + ", you won!!!! Because the AI guessed the wrong character"
                : "Sorry, " + player + " the AI guessed your character, you lost.";
        JOptionPane.showMessageDialog(null, outcome, "Message", JOptionPane.INFORMATION_MESSAGE);
        gameOver.gameFinished(outcome);
    }

    private void chooseAskOrGuess() {
        if ("1".equals(askOrGuess.getSelectedItem())) {
            prompt.setText("Please choice the question you want to ask: ");
            questions.setModel(new DefaultComboBoxModel<>(
                    controller.game().getCurrentPlayerQuestionTexts()));
            showOnly(questions, confirmQuestion);
            return;
        }
        prompt.setText(controller.game().getCurrentPlayerName() + ", please enter your guess: ");
        characters.setModel(new DefaultComboBoxModel<>(controller.game().getCharacterNames()));
        showOnly(characters, confirmGuess);
    }

    private void askChosenQuestion() {
        String question = (String) questions.getSelectedItem();
        String answer = controller.game().askComputer(question);
        answerShown.setText("AI: " + answer);
        history.recordForFirst(LabelText.escaped(question) + " : " + answer);
        showOnly(nextTurn);
    }

    private void guessChosenCharacter() {
        gameOver.gameFinished(
                controller.game().guessComputer((String) characters.getSelectedItem()));
    }

    private void answerComputer() {
        String answer = (String) yesOrNo.getSelectedItem();
        String asked = prompt.getText();
        controller.game().answerComputerQuestion("yes".equals(answer));
        history.recordForSecond(LabelText.escaped(asked) + " : " + answer);
        showOnly(nextTurn);
    }

    /** Shows exactly these controls, so no stale one is left behind. */
    private void showOnly(java.awt.Component... visible) {
        for (java.awt.Component control : new java.awt.Component[] {
                askOrGuess, confirmChoice, questions, confirmQuestion,
                characters, confirmGuess, yesOrNo, confirmAnswer, nextTurn}) {
            control.setVisible(false);
        }
        for (java.awt.Component control : visible) {
            control.setVisible(true);
        }
        revalidate();
        repaint();
    }
}
