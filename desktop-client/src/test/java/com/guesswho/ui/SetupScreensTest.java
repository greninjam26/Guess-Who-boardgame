package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.QuestionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SetupScreensTest {
    private final List<String> errors = new ArrayList<>();
    private final AtomicReference<OpeningTurn> completedWith = new AtomicReference<>();
    private final GameSetup setup = new GameSetup();

    @Test
    void welcomeScreenHasAReadableVisualHierarchy() throws Exception {
        SetupScreens screens = screens();
        JPanel welcome = visibleCard(screens.panel());
        List<JLabel> labels = labels(welcome);

        assertEquals("Guess Who?", labels.get(0).getText());
        assertEquals("Ask clever questions. Find the mystery character.",
                labels.get(1).getText());
        assertTrue(labels.get(0).getFont().getSize2D()
                        > labels.get(1).getFont().getSize2D(),
                "The game title should be more prominent than supporting copy");

        JButton start = findButton(welcome, "Start game");
        assertTrue(start.getPreferredSize().height >= 40,
                "The primary action should be easy to target");
        assertTrue(contrast(start.getForeground(), start.getBackground()) >= 4.5,
                "The primary action text should remain readable");
    }

    @Test
    void choosingAComputerModeOffersTheComputerAsAStarter() throws Exception {
        SetupScreens screens = screens();

        click(screens, "Play vs computer — Hard");

        assertTrue(setup.isAgainstComputer());
        assertEquals(ComputerDifficulty.HARD, setup.difficulty());
        enterName(screens, "Alex");
        assertTrue(hasButton(screens, "AI goes first"));
        assertFalse(hasButton(screens, "Younger player goes first"),
                "There is no second birthday to compare in a computer game");
    }

    @Test
    void choosingATwoPlayerModeOffersTheSecondPlayerAndBirthdays() throws Exception {
        SetupScreens screens = screens();

        click(screens, "Two players — Custom questions");

        assertEquals(QuestionMode.FREE_FORM, setup.questionMode());
        enterName(screens, "Alex");
        enterText(screens, "20000101");
        enterName(screens, "Blake");
        enterText(screens, "20010101");
        assertTrue(hasButton(screens, "Younger player goes first"));
        assertFalse(hasButton(screens, "AI goes first"));
    }

    @Test
    void reportsABlankUsernameInsteadOfAcceptingIt() throws Exception {
        SetupScreens screens = screens();
        click(screens, "Play vs computer — Easy");

        enterName(screens, "   ");

        assertEquals(List.of("Username must not be blank."), errors);
        assertNull(setup.firstUsername());
    }

    @Test
    void refusesTheReservedComputerNameInAComputerGame() throws Exception {
        SetupScreens screens = screens();
        click(screens, "Play vs computer — Easy");

        enterName(screens, "AI");

        assertEquals(List.of("AI is reserved for the computer player."), errors);
    }

    @Test
    void refusesTwoPlayersSharingAName() throws Exception {
        SetupScreens screens = screens();
        click(screens, "Two players — Preset questions");
        enterName(screens, "Alex");
        enterText(screens, "20000101");

        enterName(screens, "Alex");

        assertEquals(List.of("Player usernames must be different."), errors);
    }

    @Test
    void reportsAnUnreadableBirthdayInsteadOfThrowing() throws Exception {
        SetupScreens screens = screens();
        click(screens, "Two players — Preset questions");
        enterName(screens, "Alex");

        enterText(screens, "not a date");

        assertEquals(List.of("Birthday must be digits in the form YYYYMMDD."), errors);
        assertEquals(0, setup.firstBirthday());
    }

    @Test
    void reportsTheChosenOpeningTurnOnceEverythingIsAnswered() throws Exception {
        SetupScreens screens = screens();
        click(screens, "Play vs computer — Easy");
        enterName(screens, "Alex");

        click(screens, "AI goes first");

        assertEquals(OpeningTurn.COMPUTER, completedWith.get());
    }

    @Test
    void centresTheOpeningTurnPrompt() throws Exception {
        SetupScreens screens = screens();
        click(screens, "Play vs computer — Easy");
        enterName(screens, "Alex");

        JLabel prompt = findLabel(visibleCard(screens.panel()));

        assertEquals(SwingConstants.CENTER, prompt.getHorizontalAlignment(),
                "A label in a BorderLayout region stretches, so it must centre its own text");
    }

    @Test
    void modeScreenPresentsScannableFullWidthChoices() throws Exception {
        SetupScreens screens = screens();

        click(screens, "Start game");
        JPanel mode = visibleCard(screens.panel());

        assertEquals("Choose a game mode", labels(mode).get(0).getText());
        List<JButton> choices = buttons(mode);
        assertEquals(List.of(
                "Play vs computer — Easy",
                "Play vs computer — Hard",
                "Play vs computer — Custom questions",
                "Two players — Preset questions",
                "Two players — Custom questions",
                "Play online with a friend"),
                choices.stream().map(JButton::getText).toList());
        assertTrue(choices.stream().allMatch(button -> button.getPreferredSize().height >= 40),
                "Every mode should be easy to target");
        assertEquals(1, choices.stream().map(button -> button.getPreferredSize().width)
                .distinct().count(), "Mode choices should form one aligned column");
    }

    @Test
    void nameStepUsesFocusedPromptAndClearConfirmation() throws Exception {
        SetupScreens screens = screens();

        click(screens, "Play vs computer — Easy");
        JPanel nameStep = visibleCard(screens.panel());

        assertEquals("Your name", labels(nameStep).get(0).getText());
        assertEquals("Enter the name shown during this game.",
                labels(nameStep).get(1).getText());
        assertTrue(findField(nameStep).getPreferredSize().height >= 36,
                "Text fields should be easy to focus and read");
        assertTrue(findButton(nameStep, "Confirm").getPreferredSize().height >= 40,
                "The confirmation action should be easy to target");
    }

    @Test
    void openingTurnStepUsesClearAlignedChoices() throws Exception {
        SetupScreens screens = screens();
        click(screens, "Play vs computer — Easy");
        enterName(screens, "Alex");

        JPanel openingTurn = visibleCard(screens.panel());
        assertEquals("Who goes first?", labels(openingTurn).get(0).getText());
        List<JButton> choices = buttons(openingTurn);
        assertEquals(List.of("Alex goes first", "AI goes first", "Pick randomly"),
                choices.stream().map(JButton::getText).toList());
        assertTrue(choices.stream().allMatch(button -> button.getPreferredSize().height >= 40));
        assertEquals(1, choices.stream().map(button -> button.getPreferredSize().width)
                .distinct().count(), "Opening-turn choices should form one aligned column");
    }

    private JLabel findLabel(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) {
                return label;
            }
            if (child instanceof Container nested) {
                JLabel found = findLabel(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private List<JLabel> labels(Container container) {
        List<JLabel> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) {
                found.add(label);
            }
            else if (child instanceof Container nested) {
                found.addAll(labels(nested));
            }
        }
        return found;
    }

    private List<JButton> buttons(Container container) {
        List<JButton> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button) {
                found.add(button);
            }
            else if (child instanceof Container nested) {
                found.addAll(buttons(nested));
            }
        }
        return found;
    }

    private double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private double luminance(Color color) {
        double red = channel(color.getRed());
        double green = channel(color.getGreen());
        double blue = channel(color.getBlue());
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private double channel(int value) {
        double ratio = value / 255.0;
        return ratio <= 0.04045
                ? ratio / 12.92
                : Math.pow((ratio + 0.055) / 1.055, 2.4);
    }

    // --- helpers -------------------------------------------------------

    private SetupScreens screens() throws Exception {
        AtomicReference<SetupScreens> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new SetupScreens(
                setup, errors::add, completedWith::set)));
        return reference.get();
    }

    private void click(SetupScreens screens, String label) throws Exception {
        JButton button = findButton(screens.panel(), label);
        SwingUtilities.invokeAndWait(button::doClick);
    }

    private void enterName(SetupScreens screens, String value) throws Exception {
        enterText(screens, value);
    }

    private void enterText(SetupScreens screens, String value) throws Exception {
        JPanel card = visibleCard(screens.panel());
        JTextField field = findField(card);
        SwingUtilities.invokeAndWait(() -> field.setText(value));
        SwingUtilities.invokeAndWait(findButton(card, "Confirm")::doClick);
    }

    private boolean hasButton(SetupScreens screens, String label) {
        return findButtonOrNull(visibleCard(screens.panel()), label) != null;
    }

    private JPanel visibleCard(Container root) {
        for (Component child : root.getComponents()) {
            if (child.isVisible() && child instanceof JPanel panel) {
                return panel;
            }
        }
        throw new AssertionError("No card is showing");
    }

    private JButton findButton(Container container, String label) {
        JButton button = findButtonOrNull(container, label);
        if (button == null) {
            throw new AssertionError("No button labelled " + label);
        }
        return button;
    }

    private JButton findButtonOrNull(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && label.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container nested) {
                JButton found = findButtonOrNull(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JTextField findField(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTextField field) {
                return field;
            }
            if (child instanceof Container nested) {
                JTextField found = findField(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
