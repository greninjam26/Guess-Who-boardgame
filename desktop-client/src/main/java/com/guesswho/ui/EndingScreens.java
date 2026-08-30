package com.guesswho.ui;

import com.guesswho.game.AnswerCorrection;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * What happens once a game is over: each player names the character they were
 * holding, both are revealed, and — against the computer — the answers they
 * gave are checked against the character they claim.
 *
 * <p>Both characters were chosen before play began, so there is nothing left to
 * ask here. Against the computer the answers given are replayed against the
 * character that was actually committed to, which is what makes the check
 * meaningful rather than a comparison against a later claim.</p>
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

    /** Notified when the players want another game. */
    @FunctionalInterface
    interface Rematch {
        /** Called when Play again is chosen. */
        void playAgain();
    }

    private static final String NAME_FIRST = "nameFirst";
    private static final String NAME_SECOND = "nameSecond";
    private static final String REVEAL = "reveal";

    private final GameController controller;
    private final CharacterImages images;
    private final Completion completion;
    private final Rematch rematch;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JPanel revealPanel = new JPanel(new BorderLayout(0, 12));
    private final JPanel portraits = new JPanel(new GridLayout(1, 2, 24, 0));
    private final JButton playAgain = new JButton("Play again");
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
     * @param rematch notified when the players want another game
     */
    EndingScreens(
            GameController controller,
            CharacterImages images,
            Completion completion,
            Rematch rematch) {
        this.controller = controller;
        this.images = images;
        this.completion = completion;
        this.rematch = rematch;
        root.add(namePanel("Which character did you have?", firstChoice, this::nameFirst),
                NAME_FIRST);
        root.add(namePanel("Second player, which character did you have?",
                secondChoice, this::nameSecond), NAME_SECOND);
        revealPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel below = new JPanel(new BorderLayout(0, 8));
        below.add(validationLabel, BorderLayout.CENTER);
        JPanel again = new JPanel(new FlowLayout(FlowLayout.CENTER));
        again.add(playAgain);
        below.add(again, BorderLayout.SOUTH);
        revealPanel.add(outcomeLabel, BorderLayout.NORTH);
        revealPanel.add(portraits, BorderLayout.CENTER);
        revealPanel.add(below, BorderLayout.SOUTH);
        outcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        validationLabel.setHorizontalAlignment(SwingConstants.CENTER);
        playAgain.addActionListener(event -> rematch.playAgain());
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
        //Rebuilt each time: a second game would otherwise show both results.
        portraits.removeAll();
        validationLabel.setText("");
        outcomeLabel.setText(outcome);
        if (!controller.setup().tellsCharacterUpFront()) {
            String[] characters = controller.game().getCharacterNames();
            firstChoice.setModel(new javax.swing.DefaultComboBoxModel<>(characters));
            secondChoice.setModel(new javax.swing.DefaultComboBoxModel<>(characters));
            cards.show(root, NAME_FIRST);
            return;
        }
        if (controller.setup().isAgainstComputer()) {
            revealAgainstComputer();
            return;
        }
        revealBetweenPlayers();
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
        revealBetweenPlayers();
    }

    private void revealBetweenPlayers() {
        portraits.add(revealed(controller.setup().firstUsername(),
                portraitOf(controller.setup().firstUsername())));
        portraits.add(revealed(controller.setup().secondUsername(),
                portraitOf(controller.setup().secondUsername())));
        cards.show(root, REVEAL);
        completion.revealComplete(true);
    }

    /** A portrait says nothing on its own about whose character it was. */
    private JPanel revealed(String who, JLabel portrait) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JLabel name = new JLabel(LabelText.escaped(who), SwingConstants.CENTER);
        portrait.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(portrait, BorderLayout.CENTER);
        panel.add(name, BorderLayout.SOUTH);
        return panel;
    }

    private void revealAgainstComputer() {
        portraits.add(revealed(controller.setup().firstUsername(),
                portraitOf(controller.setup().firstUsername())));
        portraits.add(revealed("AI", new JLabel(images.portrait(
                controller.game().getComputerSelectedCharacterIndex()))));

        List<AnswerCorrection> corrections = controller.game().getComputerAnswerCorrections();
        validationLabel.setText(validationText(corrections));
        cards.show(root, REVEAL);
        completion.revealComplete(corrections.isEmpty());
    }

    private String validationText(List<AnswerCorrection> corrections) {
        if (corrections.isEmpty()) {
            return "<html>Every answer matched your character."
                    + (controller.setup().tellsCharacterUpFront()
                            ? "<br>You committed to it before the questions began,"
                                    + " so that is settled."
                            : "<br>You named your character just now, so this only shows"
                                    + " your answers were consistent with it.")
                    + "<br>Your game result will be stored.</html>";
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
