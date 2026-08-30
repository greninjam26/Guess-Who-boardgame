package com.guesswho.ui;

import com.guesswho.game.AnswerCorrection;

import java.awt.CardLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * What happens once a game is over: each player names the character they were
 * holding, both are revealed, and — against the computer — the answers they
 * gave are checked against the character they claim.
 *
 * <p>Characters are named now rather than at the start because the game does
 * not know them during play; a player holds theirs in their head. That is also
 * why the check is worth running: a player who answered carelessly, or
 * dishonestly, produces answers that do not match the character they name at
 * the end.</p>
 */
class EndingScreens {
    /** Notified once both characters are on screen. */
    @FunctionalInterface
    interface Completion {
        /**
         * Called when the reveal is finished.
         *
         * @param resultTrustworthy whether the answers given match the character
         *        named, and so whether the result is worth storing
         */
        void revealComplete(boolean resultTrustworthy);
    }

    private static final String NAME_FIRST = "nameFirst";
    private static final String NAME_SECOND = "nameSecond";
    private static final String REVEAL = "reveal";

    private final GameController controller;
    private final CharacterImages images;
    private final Completion completion;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JPanel revealPanel = new JPanel();
    private final JLabel outcomeLabel = new JLabel();
    private final JLabel validationLabel = new JLabel();
    private final JComboBox<String> firstChoice = new JComboBox<>();
    private final JComboBox<String> secondChoice = new JComboBox<>();

    /**
     * Builds the ending screens.
     *
     * @param controller the finished game
     * @param images portraits used to show the revealed characters
     * @param completion notified once both characters are on screen
     */
    EndingScreens(GameController controller, CharacterImages images, Completion completion) {
        this.controller = controller;
        this.images = images;
        this.completion = completion;
        root.add(namePanel("Which character did you have?", firstChoice, this::nameFirst),
                NAME_FIRST);
        root.add(namePanel("Second player, which character did you have?",
                secondChoice, this::nameSecond), NAME_SECOND);
        root.add(revealPanel, REVEAL);
    }

    /**
     * Returns the panel holding every ending screen.
     *
     * @return the ending panel
     */
    JPanel panel() {
        return root;
    }

    /**
     * Shows the first character prompt.
     *
     * @param outcome message describing who won and why
     */
    void begin(String outcome) {
        outcomeLabel.setText(outcome);
        String[] characters = controller.game().getCharacterNames();
        firstChoice.setModel(new javax.swing.DefaultComboBoxModel<>(characters));
        secondChoice.setModel(new javax.swing.DefaultComboBoxModel<>(characters));
        cards.show(root, NAME_FIRST);
    }

    private JPanel namePanel(String prompt, JComboBox<String> choice, Runnable accept) {
        JPanel panel = new JPanel();
        JButton confirm = new JButton("Comfirm");
        confirm.addActionListener(event -> accept.run());
        panel.add(new JLabel(prompt));
        panel.add(choice);
        panel.add(confirm);
        return panel;
    }

    private void nameFirst() {
        controller.game().selectCharacter(
                controller.setup().firstUsername(), (String) firstChoice.getSelectedItem());
        if (controller.setup().isAgainstComputer()) {
            revealAgainstComputer();
            return;
        }
        cards.show(root, NAME_SECOND);
    }

    private void nameSecond() {
        controller.game().selectCharacter(
                controller.setup().secondUsername(), (String) secondChoice.getSelectedItem());
        revealPanel.add(portraitOf(controller.setup().secondUsername()));
        revealPanel.add(outcomeLabel);
        revealPanel.add(portraitOf(controller.setup().firstUsername()));
        cards.show(root, REVEAL);
        completion.revealComplete(true);
    }

    private void revealAgainstComputer() {
        revealPanel.add(new JLabel(images.portrait(
                controller.game().getComputerSelectedCharacterIndex())));
        revealPanel.add(outcomeLabel);
        revealPanel.add(portraitOf(controller.setup().firstUsername()));

        List<AnswerCorrection> corrections = controller.game().getComputerAnswerCorrections();
        validationLabel.setText(validationText(corrections));
        revealPanel.add(validationLabel);
        cards.show(root, REVEAL);
        completion.revealComplete(corrections.isEmpty());
    }

    private String validationText(List<AnswerCorrection> corrections) {
        if (corrections.isEmpty()) {
            return "<html>Your answer to the questions is all correct!!!"
                    + "<br>Thank you for doing to correctly!! :)"
                    + "<br>your game result will be stored</html>";
        }
        StringBuilder text = new StringBuilder("<html>you answered ")
                .append(corrections.size())
                .append(" questions wrong!!! :( <br> your game result will not be saved<br>");
        for (AnswerCorrection correction : corrections) {
            text.append(correction.question())
                    .append(" : ")
                    .append(correction.expectedAnswer() ? "yes" : "no")
                    .append(" <br>");
        }
        return text.append("</html>").toString();
    }

    private JLabel portraitOf(String username) {
        return new JLabel(images.portrait(
                controller.game().getSelectedCharacterIndex(username)));
    }
}
