package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.QuestionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
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
    void choosingAComputerModeOffersTheComputerAsAStarter() throws Exception {
        SetupScreens screens = screens();

        click(screens, "player vs computer hard mode");

        assertTrue(setup.isAgainstComputer());
        assertEquals(ComputerDifficulty.HARD, setup.difficulty());
        enterName(screens, "Alex");
        assertTrue(hasButton(screens, "AI goes first"));
        assertFalse(hasButton(screens, "Younger person go first"),
                "There is no second birthday to compare in a computer game");
    }

    @Test
    void choosingATwoPlayerModeOffersTheSecondPlayerAndBirthdays() throws Exception {
        SetupScreens screens = screens();

        click(screens, "player vs player ask questions");

        assertEquals(QuestionMode.FREE_FORM, setup.questionMode());
        enterName(screens, "Alex");
        enterText(screens, "20000101");
        enterName(screens, "Blake");
        enterText(screens, "20010101");
        assertTrue(hasButton(screens, "Younger person go first"));
        assertFalse(hasButton(screens, "AI goes first"));
    }

    @Test
    void reportsABlankUsernameInsteadOfAcceptingIt() throws Exception {
        SetupScreens screens = screens();
        click(screens, "player vs computer easy mode");

        enterName(screens, "   ");

        assertEquals(List.of("Username must not be blank."), errors);
        assertNull(setup.firstUsername());
    }

    @Test
    void refusesTheReservedComputerNameInAComputerGame() throws Exception {
        SetupScreens screens = screens();
        click(screens, "player vs computer easy mode");

        enterName(screens, "AI");

        assertEquals(List.of("AI is reserved for the computer player."), errors);
    }

    @Test
    void refusesTwoPlayersSharingAName() throws Exception {
        SetupScreens screens = screens();
        click(screens, "player vs player preset questions");
        enterName(screens, "Alex");
        enterText(screens, "20000101");

        enterName(screens, "Alex");

        assertEquals(List.of("Player usernames must be different."), errors);
    }

    @Test
    void reportsAnUnreadableBirthdayInsteadOfThrowing() throws Exception {
        SetupScreens screens = screens();
        click(screens, "player vs player preset questions");
        enterName(screens, "Alex");

        enterText(screens, "not a date");

        assertEquals(List.of("Birthday must be digits in the form YYYYMMDD."), errors);
        assertEquals(0, setup.firstBirthday());
    }

    @Test
    void reportsTheChosenOpeningTurnOnceEverythingIsAnswered() throws Exception {
        SetupScreens screens = screens();
        click(screens, "player vs computer easy mode");
        enterName(screens, "Alex");

        click(screens, "AI goes first");

        assertEquals(OpeningTurn.COMPUTER, completedWith.get());
    }

    @Test
    void centresTheOpeningTurnPrompt() throws Exception {
        SetupScreens screens = screens();
        click(screens, "player vs computer easy mode");
        enterName(screens, "Alex");

        JLabel prompt = findLabel(visibleCard(screens.panel()));

        assertEquals(SwingConstants.CENTER, prompt.getHorizontalAlignment(),
                "A label in a BorderLayout region stretches, so it must centre its own text");
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
        SwingUtilities.invokeAndWait(findButton(card, "Comfirm")::doClick);
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
