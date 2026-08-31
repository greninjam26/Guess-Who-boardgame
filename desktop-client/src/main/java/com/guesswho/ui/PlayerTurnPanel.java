package com.guesswho.ui;

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
    private Runnable onTurnChange = () -> {
    };

    private final JLabel prompt = new JLabel();
    private final JLabel answerShown = new JLabel("");
    private final JComboBox<String> presetQuestions = new JComboBox<>();
    private final JTextField typedQuestion = new JTextField(24);
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
        //Prompt above, controls in a row, the answer below. Placing these by
        //hand only lined up at one window size.
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.controller = controller;
        this.history = history;
        this.boards = boards;

        prompt.setHorizontalAlignment(SwingConstants.CENTER);
        answerShown.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        controls.add(controller.setup().isFreeFormQuestions() ? typedQuestion : presetQuestions);
        controls.add(ask);
        controls.add(next);
        controls.add(guess);
        add(prompt, BorderLayout.NORTH);
        add(controls, BorderLayout.CENTER);
        add(answerShown, BorderLayout.SOUTH);

        ask.addActionListener(event -> askOpponent());
        guess.addActionListener(event -> boards.showGuessBoard());
        next.addActionListener(event -> endTurn());
    }

    /**
     * Prepares the controls for whoever's turn it now is.
     */
    void beginTurn() {
        onTurnChange.run();
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

    /**
     * Told whenever a turn begins, so a game in progress can be kept.
     *
     * <p>A listener rather than a constructor argument: it is something that
     * watches turns happen, not something the panel needs in order to work.</p>
     *
     * @param listener run at the start of each turn
     */
    void onTurnChange(Runnable listener) {
        onTurnChange = listener;
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
