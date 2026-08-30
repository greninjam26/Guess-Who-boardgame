package com.guesswho.ui;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.QuestionMode;
import com.guesswho.game.Game;
import com.guesswho.game.GameStatus;

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
import javax.swing.JTextField;
import javax.swing.JPanel;
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

        long visibleControls = buttons(panel).stream()
                .filter(Component::isVisible)
                .count();

        assertEquals(1, visibleControls,
                "A control left over from an earlier state would still be clickable");
    }

    @Test
    void offersATextFieldWhenQuestionsAreTyped() throws Exception {
        ComputerTurnPanel panel = panelFor(freeFormGame());
        begin(panel);
        choose(panel, 0, "1");

        click(panel, "Comfirm");

        assertTrue(controls(panel, JTextField.class).get(0).isVisible());
        assertFalse(combo(panel, 1).isVisible(), "The board's questions are not offered");
    }

    @Test
    void saysWhenItCannotAnswerAndLetsThePlayerAskAgain() throws Exception {
        GameController controller = freeFormGame();
        ComputerTurnPanel panel = panelFor(controller);
        begin(panel);
        choose(panel, 0, "1");
        click(panel, "Comfirm");
        JTextField typed = controls(panel, JTextField.class).get(0);

        SwingUtilities.invokeAndWait(() -> typed.setText("do they look friendly?"));
        clickSecond(panel, "Comfirm");

        assertTrue(labelText(panel).contains("cannot answer"), labelText(panel));
        assertFalse(button(panel, "Next Turn").isVisible(),
                "The turn has not passed, so there is nothing to move on from");
        assertTrue(typed.isVisible(), "The player needs the field to ask again");
    }

    @Test
    void answersATypedQuestionItUnderstands() throws Exception {
        GameController controller = freeFormGame();
        ComputerTurnPanel panel = panelFor(controller);
        begin(panel);
        choose(panel, 0, "1");
        click(panel, "Comfirm");
        JTextField typed = controls(panel, JTextField.class).get(0);

        SwingUtilities.invokeAndWait(() -> typed.setText("do they wear glasses?"));
        clickSecond(panel, "Comfirm");

        assertTrue(labelText(panel).contains("AI:"), labelText(panel));
        assertTrue(button(panel, "Next Turn").isVisible());
    }

    private GameController freeFormGame() throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstComputer(ComputerDifficulty.HARD, QuestionMode.FREE_FORM);
        setup.firstUsername("Alex");
        GameController controller = new GameController(new Game(), setup);
        controller.start(OpeningTurn.FIRST_PLAYER);
        history.begin("Alex", "AI");
        return controller;
    }

    private String labelText(ComputerTurnPanel panel) {
        StringBuilder text = new StringBuilder();
        for (JLabel label : controls(panel, JLabel.class)) {
            if (label.getText() != null) {
                text.append(label.getText());
            }
        }
        return text.toString();
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
        setup.againstComputer(ComputerDifficulty.HARD, QuestionMode.PRESET);
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
        List<JButton> matches = buttons(panel).stream()
                .filter(candidate -> label.equals(candidate.getText()))
                .toList();
        SwingUtilities.invokeAndWait(matches.get(1)::doClick);
    }

    private void choose(ComputerTurnPanel panel, int index, String value) throws Exception {
        SwingUtilities.invokeAndWait(() -> combo(panel, index).setSelectedItem(value));
    }

    private JButton button(ComputerTurnPanel panel, String label) {
        for (JButton candidate : buttons(panel)) {
            if (label.equals(candidate.getText())) {
                return candidate;
            }
        }
        throw new AssertionError("No button labelled " + label);
    }

    private JComboBox<?> combo(ComputerTurnPanel panel, int index) {
        return controls(panel, JComboBox.class).get(index);
    }

    private List<JButton> buttons(ComputerTurnPanel panel) {
        return controls(panel, JButton.class);
    }

    /**
     * Collects controls from the panel and any nested panel, but no deeper — a
     * JComboBox holds its own arrow JButton and would otherwise be found here.
     */
    @SuppressWarnings("unchecked")
    private <T extends Component> List<T> controls(Container container, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                found.add((T) child);
            }
            else if (child instanceof JPanel nested) {
                found.addAll(controls(nested, type));
            }
        }
        return found;
    }
}
