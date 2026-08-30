package com.guesswho.ui;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * The turn controls for two people sharing one machine.
 *
 * <p>Both players use the same controls, one after the other, so a turn ends by
 * swapping which board is on screen rather than by changing anything here. The
 * question input is a list of the board's questions, or a free text field, set
 * once from the mode the game started in.</p>
 */
class PlayerTurnPanel extends JPanel {
    /** Board changes the panel cannot make itself. */
    interface Boards {
        /** Shows the board belonging to whoever's turn it now is. */
        void showBoardForCurrentPlayer();

        /** Shows the board used to name a guess. */
        void showGuessBoard();
    }

    private final GameController controller;
    private final QuestionHistory history;
    private final Boards boards;

    private final JLabel prompt = new JLabel();
    private final JLabel answerShown = new JLabel("");
    private final JComboBox<String> presetQuestions = new JComboBox<>();
    private final JTextField typedQuestion = new JTextField();
    private final JButton ask = new JButton("ask question");
    private final JButton next = new JButton("next");
    private final JButton guess = new JButton("guess");

    /**
     * Builds the controls.
     *
     * @param controller the game in progress
     * @param history transcript to record questions and answers in
     * @param boards board changes the panel asks for
     */
    PlayerTurnPanel(GameController controller, QuestionHistory history, Boards boards) {
        super(null);
        this.controller = controller;
        this.history = history;
        this.boards = boards;

        prompt.setHorizontalAlignment(SwingConstants.CENTER);
        prompt.setBounds(390, 625, 600, 30);
        answerShown.setHorizontalAlignment(SwingConstants.CENTER);
        answerShown.setBounds(0, 705, 1350, 30);
        presetQuestions.setBounds(490, 675, 300, 30);
        typedQuestion.setBounds(490, 675, 300, 30);
        ask.setBounds(790, 675, 150, 30);
        next.setBounds(940, 675, 75, 30);
        guess.setBounds(1015, 675, 100, 30);

        add(prompt);
        add(answerShown);
        add(ask);
        add(next);
        add(guess);
        add(controller.setup().isFreeFormQuestions() ? typedQuestion : presetQuestions);

        ask.addActionListener(event -> askOpponent());
        guess.addActionListener(event -> boards.showGuessBoard());
        next.addActionListener(event -> endTurn());
    }

    /**
     * Prepares the controls for whoever's turn it now is.
     */
    void beginTurn() {
        prompt.setText(controller.game().getCurrentPlayerName()
                + (controller.setup().isFreeFormQuestions()
                        ? ", input a question or guess the character"
                                + " (don't make the question go over 43 letters including space)"
                        : ", Choose a question or guess the character"));
        if (!controller.setup().isFreeFormQuestions()) {
            presetQuestions.setModel(new DefaultComboBoxModel<>(
                    controller.game().getCurrentPlayerQuestionTexts()));
        }
        answerShown.setText("");
        ask.setEnabled(true);
        boards.showBoardForCurrentPlayer();
    }

    private void askOpponent() {
        String question = controller.setup().isFreeFormQuestions()
                ? typedQuestion.getText()
                : (String) presetQuestions.getSelectedItem();
        if (controller.setup().isFreeFormQuestions()) {
            typedQuestion.setText("");
        }
        String asker = controller.game().getCurrentPlayerName();
        int reply = JOptionPane.showConfirmDialog(
                null, asker + ", " + question, "Confirmation", JOptionPane.YES_NO_OPTION);
        boolean answeredYes = reply == JOptionPane.YES_OPTION;
        controller.game().recordPlayerQuestion(asker, question, answeredYes);

        String answer = answeredYes ? "yes" : "no";
        String entry = LabelText.escaped(question) + "  " + answer + ".";
        if (asker.equals(controller.setup().firstUsername())) {
            history.recordForFirst(entry);
        }
        else {
            history.recordForSecond(entry);
        }
        answerShown.setText(answer);
        ask.setEnabled(false);
    }

    private void endTurn() {
        controller.game().advanceTurn();
        beginTurn();
    }
}
