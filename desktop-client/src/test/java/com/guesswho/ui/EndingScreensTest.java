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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndingScreensTest {
    private static CharacterImages images;
    private final List<Boolean> trustworthy = new ArrayList<>();

    @BeforeAll
    static void loadImages() throws Exception {
        AtomicReference<CharacterImages> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new CharacterImages()));
        images = reference.get();
    }

    @Test
    void trustsAComputerGameWhereEveryAnswerMatched() throws Exception {
        GameController controller = finishedComputerGame(true);
        EndingScreens screens = screensFor(controller);

        SwingUtilities.invokeAndWait(() -> screens.begin("You won"));
        confirm(screens);

        assertEquals(List.of(true), trustworthy);
    }

    @Test
    void doesNotTrustAComputerGameWithAMismatchedAnswer() throws Exception {
        GameController controller = finishedComputerGame(false);
        EndingScreens screens = screensFor(controller);

        SwingUtilities.invokeAndWait(() -> screens.begin("You won"));
        confirm(screens);

        assertEquals(List.of(false), trustworthy,
                "An answer that does not match the character named must not be stored");
    }

    @Test
    void explainsWhichAnswersDidNotMatch() throws Exception {
        GameController controller = finishedComputerGame(false);
        EndingScreens screens = screensFor(controller);

        SwingUtilities.invokeAndWait(() -> screens.begin("You won"));
        confirm(screens);

        String shown = labelText(visibleCard(screens.panel()));
        assertTrue(shown.contains("questions wrong"), shown);
        assertTrue(shown.contains("will not be saved"), shown);
    }

    @Test
    void asksBothPlayersInATwoPlayerGame() throws Exception {
        GameController controller = finishedPlayerGame();
        EndingScreens screens = screensFor(controller);

        SwingUtilities.invokeAndWait(() -> screens.begin("Alex won"));
        confirm(screens);

        assertTrue(trustworthy.isEmpty(), "The reveal waits for the second player");
        confirm(screens);
        assertEquals(List.of(true), trustworthy);
    }

    @Test
    void recordsWhatEachPlayerNamed() throws Exception {
        GameController controller = finishedPlayerGame();
        EndingScreens screens = screensFor(controller);
        SwingUtilities.invokeAndWait(() -> screens.begin("Alex won"));

        choose(screens, "Sam");
        confirm(screens);
        choose(screens, "Olivia");
        confirm(screens);

        assertEquals("Sam", controller.game().getGameResult()
                .participants().get(0).selectedCharacter());
        assertEquals("Olivia", controller.game().getGameResult()
                .participants().get(1).selectedCharacter());
    }

    // --- helpers -------------------------------------------------------

    private EndingScreens screensFor(GameController controller) throws Exception {
        AtomicReference<EndingScreens> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                new EndingScreens(controller, images, trustworthy::add)));
        return reference.get();
    }

    /** Plays one computer game, answering either truthfully or not, then finishes it. */
    private GameController finishedComputerGame(boolean answerTruthfully) throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstComputer(ComputerDifficulty.HARD);
        setup.firstUsername("Alex");
        GameController controller = new GameController(new Game(), setup);
        controller.start(OpeningTurn.COMPUTER);

        var question = controller.game().playComputerQuestion();
        boolean truthfulAnswer = controller.game().getFirstPlayer().getGameBoard().getAnswers()
                [controller.game().getFirstPlayer().findCharacter("Sam").getCharacterIndex()]
                [question.getQuestionIndex()];
        controller.game().answerComputerQuestion(
                answerTruthfully ? truthfulAnswer : !truthfulAnswer);
        controller.game().finish("Alex");
        return controller;
    }

    private GameController finishedPlayerGame() throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstPlayer(QuestionMode.PRESET);
        setup.firstUsername("Alex");
        setup.firstBirthday(20000101);
        setup.secondUsername("Blake");
        setup.secondBirthday(20010101);
        GameController controller = new GameController(new Game(), setup);
        controller.start(OpeningTurn.FIRST_PLAYER);
        controller.game().finish("Alex");
        return controller;
    }

    private void confirm(EndingScreens screens) throws Exception {
        // By text, not by type: a JComboBox contains its own arrow JButton.
        JButton button = findButton(visibleCard(screens.panel()), "Comfirm");
        SwingUtilities.invokeAndWait(button::doClick);
    }

    private JButton findButton(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && label.equals(button.getText())) {
                return button;
            }
            if (child instanceof Container nested) {
                JButton found = findButton(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void choose(EndingScreens screens, String character) throws Exception {
        JComboBox<?> choice = find(visibleCard(screens.panel()), JComboBox.class);
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

    private String labelText(Container container) {
        StringBuilder text = new StringBuilder();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null) {
                text.append(label.getText());
            }
            if (child instanceof Container nested) {
                text.append(labelText(nested));
            }
        }
        return text.toString();
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> T find(Container container, Class<T> type) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                return (T) child;
            }
            if (child instanceof Container nested) {
                T found = find(nested, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void aTwoPlayerGameIsAlwaysTrusted() throws Exception {
        GameController controller = finishedPlayerGame();
        EndingScreens screens = screensFor(controller);
        SwingUtilities.invokeAndWait(() -> screens.begin("Alex won"));

        confirm(screens);
        confirm(screens);

        assertFalse(trustworthy.isEmpty());
        assertTrue(trustworthy.get(0),
                "There is no computer transcript to check a two-player game against");
    }
}
