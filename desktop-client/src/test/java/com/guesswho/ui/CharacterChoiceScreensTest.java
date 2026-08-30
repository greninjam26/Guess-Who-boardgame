package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.Game;
import com.guesswho.game.QuestionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class CharacterChoiceScreensTest {
    private final List<String> completions = new ArrayList<>();

    @Test
    void recordsTheCharacterAPlayerPicks() throws Exception {
        GameController controller = computerGame();
        CharacterChoiceScreens screens = screensFor(controller);
        begin(screens);

        choose(screens, "Olivia");
        ready(screens);

        assertEquals("Olivia",
                controller.game().getFirstPlayer().getSelectedCharacter().getName());
    }

    @Test
    void startsTheGameOnceTheOnlyPlayerHasChosen() throws Exception {
        CharacterChoiceScreens screens = screensFor(computerGame());
        begin(screens);

        ready(screens);

        assertEquals(1, completions.size(),
                "There is no second person to ask in a game against the computer");
    }

    @Test
    void asksTheSecondPlayerBeforeStartingATwoPlayerGame() throws Exception {
        CharacterChoiceScreens screens = screensFor(playerGame());
        begin(screens);

        ready(screens);

        assertTrue(completions.isEmpty(), "The second player has not chosen yet");
    }

    @Test
    void recordsBothPlayersChoices() throws Exception {
        GameController controller = playerGame();
        CharacterChoiceScreens screens = screensFor(controller);
        begin(screens);

        choose(screens, "Sam");
        ready(screens);
        choose(screens, "Nick");
        ready(screens);

        assertEquals("Sam", controller.game().getPlayer("Alex").getSelectedCharacter().getName());
        assertEquals("Nick", controller.game().getPlayer("Blake").getSelectedCharacter().getName());
        assertEquals(1, completions.size());
    }

    @Test
    void warnsEachPlayerToKeepTheirChoicePrivate() throws Exception {
        CharacterChoiceScreens screens = screensFor(playerGame());

        begin(screens);

        assertTrue(labelText(visibleCard(screens.panel())).contains("not looking"),
                labelText(visibleCard(screens.panel())));
    }

    @Test
    void namesThePlayerBeingAsked() throws Exception {
        CharacterChoiceScreens screens = screensFor(playerGame());
        begin(screens);

        String first = labelText(visibleCard(screens.panel()));
        ready(screens);
        String second = labelText(visibleCard(screens.panel()));

        assertTrue(first.contains("Alex"), first);
        assertTrue(second.contains("Blake"), second);
        assertFalse(second.contains("Alex"), second);
    }

    // --- helpers -------------------------------------------------------

    private CharacterChoiceScreens screensFor(GameController controller) throws Exception {
        AtomicReference<CharacterChoiceScreens> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                new CharacterChoiceScreens(controller, () -> completions.add("started"))));
        return reference.get();
    }

    private GameController computerGame() throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstComputer(ComputerDifficulty.HARD);
        setup.firstUsername("Alex");
        GameController controller = new GameController(new Game(), setup);
        controller.start(OpeningTurn.FIRST_PLAYER);
        return controller;
    }

    private GameController playerGame() throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstPlayer(QuestionMode.PRESET);
        setup.firstUsername("Alex");
        setup.firstBirthday(20000101);
        setup.secondUsername("Blake");
        setup.secondBirthday(20010101);
        GameController controller = new GameController(new Game(), setup);
        controller.start(OpeningTurn.FIRST_PLAYER);
        return controller;
    }

    private void begin(CharacterChoiceScreens screens) throws Exception {
        SwingUtilities.invokeAndWait(screens::begin);
    }

    private void ready(CharacterChoiceScreens screens) throws Exception {
        SwingUtilities.invokeAndWait(findButton(visibleCard(screens.panel()))::doClick);
    }

    private void choose(CharacterChoiceScreens screens, String character) throws Exception {
        JComboBox<?> choice = findCombo(visibleCard(screens.panel()));
        SwingUtilities.invokeAndWait(() -> choice.setSelectedItem(character));
    }

    private JPanel visibleCard(Container root) {
        // CardLayout only applies visibility when the container is laid out, and
        // these panels are never shown in a window.
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child.isVisible() && child instanceof JPanel panel) {
                return panel;
            }
        }
        throw new AssertionError("No card is showing");
    }

    /** By label, not by type: a JComboBox contains its own arrow JButton. */
    private JButton findButton(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && "Ready".equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("No Ready button");
    }

    private JComboBox<?> findCombo(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JComboBox<?> combo) {
                return combo;
            }
        }
        throw new AssertionError("No character list");
    }

    private String labelText(Container container) {
        StringBuilder text = new StringBuilder();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                text.append(label.getText());
            }
        }
        return text.toString();
    }
}
