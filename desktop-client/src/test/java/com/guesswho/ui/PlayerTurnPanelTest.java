package com.guesswho.ui;

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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PlayerTurnPanelTest {
    private final QuestionHistory history = new QuestionHistory();
    private final List<String> boardRequests = new ArrayList<>();

    @Test
    void offersTheBoardQuestionsInPresetMode() throws Exception {
        PlayerTurnPanel panel = panelFor(started(QuestionMode.PRESET));

        begin(panel);

        JComboBox<?> questions = find(panel, JComboBox.class);
        assertTrue(questions.getItemCount() > 0);
        assertEquals(null, find(panel, JTextField.class),
                "Preset mode does not offer a place to type");
    }

    @Test
    void offersATextFieldInFreeFormMode() throws Exception {
        PlayerTurnPanel panel = panelFor(started(QuestionMode.FREE_FORM));

        begin(panel);

        assertEquals(null, find(panel, JComboBox.class),
                "Free-form mode does not offer the board's questions");
        assertTrue(find(panel, JTextField.class) != null);
    }

    @Test
    void namesWhoseTurnItIs() throws Exception {
        PlayerTurnPanel panel = panelFor(started(QuestionMode.PRESET));

        begin(panel);

        assertTrue(prompt(panel).getText().startsWith("Alex"), prompt(panel).getText());
    }

    @Test
    void handsOverToTheOtherPlayerOnNext() throws Exception {
        GameController controller = started(QuestionMode.PRESET);
        PlayerTurnPanel panel = panelFor(controller);
        begin(panel);

        click(panel, "next");

        assertEquals("Blake", controller.game().getCurrentPlayerName());
        assertTrue(prompt(panel).getText().startsWith("Blake"), prompt(panel).getText());
    }

    @Test
    void showsTheOtherPlayersBoardOnNext() throws Exception {
        PlayerTurnPanel panel = panelFor(started(QuestionMode.PRESET));
        begin(panel);
        boardRequests.clear();

        click(panel, "next");

        assertEquals(List.of("currentPlayer"), boardRequests,
                "Each player must see their own board, not the one before it");
    }

    @Test
    void refreshesTheQuestionListForTheNewPlayer() throws Exception {
        GameController controller = started(QuestionMode.PRESET);
        PlayerTurnPanel panel = panelFor(controller);
        begin(panel);
        controller.game().recordPlayerQuestion(
                "Alex", controller.game().getCurrentPlayerQuestionTexts()[0], true);

        click(panel, "next");

        assertEquals(19, find(panel, JComboBox.class).getItemCount(),
                "Blake has asked nothing, so every question is still open to them");
    }

    @Test
    void asksForTheGuessBoardWhenGuessing() throws Exception {
        PlayerTurnPanel panel = panelFor(started(QuestionMode.PRESET));
        begin(panel);
        boardRequests.clear();

        click(panel, "guess");

        assertEquals(List.of("guessBoard"), boardRequests);
    }

    @Test
    void clearsTheLastAnswerAtTheStartOfATurn() throws Exception {
        PlayerTurnPanel panel = panelFor(started(QuestionMode.PRESET));
        begin(panel);

        click(panel, "next");

        assertTrue(answer(panel).getText().isEmpty(),
                "The previous player's answer must not linger into the next turn");
    }

    @Test
    void reEnablesAskingForTheNextPlayer() throws Exception {
        PlayerTurnPanel panel = panelFor(started(QuestionMode.PRESET));
        begin(panel);
        SwingUtilities.invokeAndWait(() -> button(panel, "ask question").setEnabled(false));

        click(panel, "next");

        assertTrue(button(panel, "ask question").isEnabled(),
                "One question per turn, but the next player gets their own");
    }

    // --- helpers -------------------------------------------------------

    private PlayerTurnPanel panelFor(GameController controller) throws Exception {
        AtomicReference<PlayerTurnPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                new PlayerTurnPanel(controller, history, new PlayerTurnPanel.Boards() {
                    @Override
                    public void showBoardForCurrentPlayer() {
                        boardRequests.add("currentPlayer");
                    }

                    @Override
                    public void showGuessBoard() {
                        boardRequests.add("guessBoard");
                    }
                })));
        return reference.get();
    }

    private GameController started(QuestionMode questionMode) throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstPlayer(questionMode);
        setup.firstUsername("Alex");
        setup.firstBirthday(20000101);
        setup.secondUsername("Blake");
        setup.secondBirthday(20010101);
        GameController controller = new GameController(new Game(), setup);
        controller.start(OpeningTurn.FIRST_PLAYER);
        history.begin("Alex", "Blake");
        return controller;
    }

    private void begin(PlayerTurnPanel panel) throws Exception {
        SwingUtilities.invokeAndWait(panel::beginTurn);
    }

    private void click(PlayerTurnPanel panel, String label) throws Exception {
        SwingUtilities.invokeAndWait(button(panel, label)::doClick);
    }

    private JLabel prompt(PlayerTurnPanel panel) {
        return controls(panel, JLabel.class).get(0);
    }

    private JLabel answer(PlayerTurnPanel panel) {
        return controls(panel, JLabel.class).get(1);
    }

    private JButton button(PlayerTurnPanel panel, String label) {
        for (JButton candidate : controls(panel, JButton.class)) {
            if (label.equals(candidate.getText())) {
                return candidate;
            }
        }
        throw new AssertionError("No button labelled " + label);
    }

    private <T extends Component> T find(PlayerTurnPanel panel, Class<T> type) {
        List<T> found = controls(panel, type);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Collects controls from the panel and any nested panel, but no deeper — a
     * JComboBox holds its own editor and arrow button, which would otherwise be
     * found here and make a preset-mode panel look as though it has a text field.
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
