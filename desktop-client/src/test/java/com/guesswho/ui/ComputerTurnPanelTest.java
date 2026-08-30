package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.Game;
import com.guesswho.game.GameStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ComputerTurnPanelTest {
    private final List<String> outcomes = new ArrayList<>();
    private final QuestionHistory history = new QuestionHistory();

    @Test
    void offersTheAskOrGuessChoiceOnThePlayersTurn() throws Exception {
        ComputerTurnPanel panel = panelFor(playerFirst());

        begin(panel);

        assertTrue(button(panel, "Comfirm").isVisible());
        assertFalse(button(panel, "Guess").isVisible());
        assertFalse(button(panel, "Next Turn").isVisible());
    }

    @Test
    void showsTheQuestionListWhenThePlayerChoosesToAsk() throws Exception {
        ComputerTurnPanel panel = panelFor(playerFirst());
        begin(panel);

        choose(panel, 0, "1");
        click(panel, "Comfirm");

        JComboBox<?> questions = combo(panel, 1);
        assertTrue(questions.isVisible());
        assertTrue(questions.getItemCount() > 0);
    }

    @Test
    void showsTheCharacterListWhenThePlayerChoosesToGuess() throws Exception {
        ComputerTurnPanel panel = panelFor(playerFirst());
        begin(panel);

        choose(panel, 0, "2");
        click(panel, "Comfirm");

        assertTrue(button(panel, "Guess").isVisible());
        assertEquals(24, combo(panel, 2).getItemCount());
    }

    @Test
    void recordsAnAskedQuestionAndItsAnswer() throws Exception {
        ComputerTurnPanel panel = panelFor(playerFirst());
        begin(panel);
        choose(panel, 0, "1");
        click(panel, "Comfirm");
        String asked = (String) combo(panel, 1).getSelectedItem();

        clickSecond(panel, "Comfirm");

        assertTrue(history.firstText().contains(LabelText.escaped(asked)), history.firstText());
        assertTrue(button(panel, "Next Turn").isVisible(),
                "After asking, the only thing left to do is take the next turn");
    }

    @Test
    void guessingResolvesTheGame() throws Exception {
        GameController controller = playerFirst();
        ComputerTurnPanel panel = panelFor(controller);
        begin(panel);
        choose(panel, 0, "2");
        click(panel, "Comfirm");

        click(panel, "Guess");

        assertEquals(1, outcomes.size());
        assertEquals(GameStatus.FINISHED, controller.game().getStatus());
    }

    @Test
    void asksThePlayerToAnswerOnTheComputersTurn() throws Exception {
        ComputerTurnPanel panel = panelFor(computerFirst());

        begin(panel);

        assertTrue(button(panel, "Confirm").isVisible());
        assertFalse(button(panel, "Comfirm").isVisible(),
                "The player is answering, not choosing what to do");
    }

    @Test
    void recordsTheAnswerGivenToTheComputer() throws Exception {
        ComputerTurnPanel panel = panelFor(computerFirst());
        begin(panel);

        click(panel, "Confirm");

        assertTrue(history.secondText().contains("yes"), history.secondText());
        assertTrue(button(panel, "Next Turn").isVisible());
    }

    @Test
    void showsOnlyOneSetOfControlsAtATime() throws Exception {
        ComputerTurnPanel panel = panelFor(playerFirst());
        begin(panel);
        choose(panel, 0, "1");
        click(panel, "Comfirm");

        long visibleControls = List.of(panel.getComponents()).stream()
                .filter(component -> component instanceof JButton && component.isVisible())
                .count();

        assertEquals(1, visibleControls,
                "A control left over from an earlier state would still be clickable");
    }

    // --- helpers -------------------------------------------------------

    private ComputerTurnPanel panelFor(GameController controller) throws Exception {
        AtomicReference<ComputerTurnPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                new ComputerTurnPanel(controller, history, outcomes::add)));
        return reference.get();
    }

    private GameController playerFirst() throws Exception {
        return started(OpeningTurn.FIRST_PLAYER);
    }

    private GameController computerFirst() throws Exception {
        return started(OpeningTurn.COMPUTER);
    }

    private GameController started(OpeningTurn openingTurn) throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstComputer(ComputerDifficulty.HARD);
        setup.firstUsername("Alex");
        GameController controller = new GameController(new Game(), setup);
        controller.start(openingTurn);
        history.begin("Alex", "AI");
        return controller;
    }

    private void begin(ComputerTurnPanel panel) throws Exception {
        SwingUtilities.invokeAndWait(panel::beginTurn);
    }

    private void click(ComputerTurnPanel panel, String label) throws Exception {
        SwingUtilities.invokeAndWait(button(panel, label)::doClick);
    }

    /** The panel holds two buttons labelled "Comfirm"; this clicks the second. */
    private void clickSecond(ComputerTurnPanel panel, String label) throws Exception {
        List<JButton> matches = new ArrayList<>();
        for (Component child : panel.getComponents()) {
            if (child instanceof JButton candidate && label.equals(candidate.getText())) {
                matches.add(candidate);
            }
        }
        SwingUtilities.invokeAndWait(matches.get(1)::doClick);
    }

    private void choose(ComputerTurnPanel panel, int index, String value) throws Exception {
        SwingUtilities.invokeAndWait(() -> combo(panel, index).setSelectedItem(value));
    }

    private JButton button(ComputerTurnPanel panel, String label) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JButton candidate && label.equals(candidate.getText())) {
                return candidate;
            }
        }
        throw new AssertionError("No button labelled " + label);
    }

    private JComboBox<?> combo(ComputerTurnPanel panel, int index) {
        List<JComboBox<?>> found = new ArrayList<>();
        for (Component child : panel.getComponents()) {
            if (child instanceof JComboBox<?> candidate) {
                found.add(candidate);
            }
        }
        return found.get(index);
    }
}
