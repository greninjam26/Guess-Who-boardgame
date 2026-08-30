package com.guesswho.ui;

import com.guesswho.game.Question;

import java.util.Optional;
import javax.swing.DefaultComboBoxModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
    private final JTextField typedQuestion = new JTextField(24);
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
        //Prompt above, controls in a row, the answer below. Placing these by
        //hand only lined up at one window size.
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.controller = controller;
        this.history = history;
        this.gameOver = gameOver;

        prompt.setHorizontalAlignment(SwingConstants.CENTER);
        answerShown.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        for (java.awt.Component control : new java.awt.Component[] {
                askOrGuess, confirmChoice, questions, typedQuestion, confirmQuestion,
                characters, confirmGuess, yesOrNo, confirmAnswer, nextTurn}) {
            control.setVisible(false);
            controls.add(control);
        }
        add(prompt, BorderLayout.NORTH);
        add(controls, BorderLayout.CENTER);
        add(answerShown, BorderLayout.SOUTH);

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
            if (controller.setup().isFreeFormQuestions()) {
                prompt.setText("Type a question about the character: ");
                typedQuestion.setText("");
                showOnly(typedQuestion, confirmQuestion);
                return;
            }
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
        String question = controller.setup().isFreeFormQuestions()
                ? typedQuestion.getText()
                : (String) questions.getSelectedItem();
        Optional<String> answer = controller.game().askComputer(question);
        if (answer.isEmpty()) {
            //The turn has not passed, so the player simply asks again.
            answerShown.setText("<html>The AI cannot answer that. Ask about eyes, hair,"
                    + " glasses, a hat, facial hair, teeth, skin tone, or piercings.</html>");
            typedQuestion.selectAll();
            typedQuestion.requestFocusInWindow();
            return;
        }
        answerShown.setText("AI: " + answer.orElseThrow());
        history.recordForFirst(
                LabelText.escaped(question) + " : " + answer.orElseThrow());
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
                askOrGuess, confirmChoice, questions, typedQuestion, confirmQuestion,
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
