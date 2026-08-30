package com.guesswho.ui;

import java.awt.CardLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Where each player picks the character their opponent will try to guess.
 *
 * <p>This used to happen after the game, because nothing recorded the choice
 * during it — a player held their character in their head and declared it at the
 * end. That made the answer review meaningless: it compared the answers given
 * against whatever character was claimed afterwards, which is exactly the thing
 * a careless or dishonest player would adjust.</p>
 *
 * <p>Two people sharing one screen take turns here, so each prompt says to keep
 * the other from looking. That is the same trust the physical game asks for when
 * you draw a card from your own tray.</p>
 */
class CharacterChoiceScreens {
    /** Notified once every player has chosen. */
    @FunctionalInterface
    interface Completion {
        /** Called when the game can begin. */
        void charactersChosen();
    }

    private static final String FIRST = "first";
    private static final String SECOND = "second";

    private final GameController controller;
    private final Completion completion;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final JComboBox<String> firstChoice = new JComboBox<>();
    private final JComboBox<String> secondChoice = new JComboBox<>();
    private final JLabel firstPrompt = new JLabel();
    private final JLabel secondPrompt = new JLabel();
    private final JCheckBox tellLater = new JCheckBox(
            "Don't tell me — I'll keep it to myself and say at the end");

    /**
     * Builds the choice screens.
     *
     * @param controller the game that has just started
     * @param completion notified once every player has chosen
     */
    CharacterChoiceScreens(GameController controller, Completion completion) {
        this.controller = controller;
        this.completion = completion;
        root.add(firstCard(), FIRST);
        root.add(choicePanel(secondPrompt, secondChoice, this::chooseSecond), SECOND);
        tellLater.addActionListener(event -> {
            firstChoice.setEnabled(!tellLater.isSelected());
            secondChoice.setEnabled(!tellLater.isSelected());
        });
    }

    /**
     * Returns the panel holding the choice screens.
     *
     * @return the choice panel
     */
    JPanel panel() {
        return root;
    }

    /**
     * Asks the first player to choose.
     */
    void begin() {
        String[] characters = controller.game().getCharacterNames();
        firstChoice.setModel(new DefaultComboBoxModel<>(characters));
        secondChoice.setModel(new DefaultComboBoxModel<>(characters));
        firstPrompt.setText(promptFor(controller.setup().firstUsername()));
        if (controller.setup().isAgainstPlayer()) {
            secondPrompt.setText(promptFor(controller.setup().secondUsername()));
        }
        cards.show(root, FIRST);
    }

    private String promptFor(String username) {
        return "<html>" + LabelText.escaped(username)
                + ", choose the character your opponent will try to guess."
                + "<br>Make sure they are not looking at the screen.</html>";
    }

    /** Only the first card offers the choice; it applies to everyone in the game. */
    private JPanel firstCard() {
        JPanel panel = choicePanel(firstPrompt, firstChoice, this::chooseFirst);
        panel.add(tellLater);
        return panel;
    }

    private JPanel choicePanel(JLabel prompt, JComboBox<String> choice, Runnable accept) {
        JPanel panel = new JPanel();
        JButton ready = new JButton("Ready");
        ready.addActionListener(event -> accept.run());
        panel.add(prompt);
        panel.add(choice);
        panel.add(ready);
        return panel;
    }

    private void chooseFirst() {
        controller.setup().tellsCharacterUpFront(!tellLater.isSelected());
        if (tellLater.isSelected()) {
            //nobody names a character now; the ending will ask everyone
            completion.charactersChosen();
            return;
        }
        controller.game().selectCharacter(
                controller.setup().firstUsername(), (String) firstChoice.getSelectedItem());
        if (controller.setup().isAgainstPlayer()) {
            cards.show(root, SECOND);
            return;
        }
        completion.charactersChosen();
    }

    private void chooseSecond() {
        controller.game().selectCharacter(
                controller.setup().secondUsername(), (String) secondChoice.getSelectedItem());
        completion.charactersChosen();
    }
}
