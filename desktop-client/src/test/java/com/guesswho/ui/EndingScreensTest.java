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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndingScreensTest {
    private static CharacterImages images;
    private final List<Boolean> trustworthy = new ArrayList<>();
    private final List<String> rematches = new ArrayList<>();

    @BeforeAll
    static void loadImages() throws Exception {
        AtomicReference<CharacterImages> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new CharacterImages()));
        images = reference.get();
    }

    @Test
    void trustsAComputerGameWhereEveryAnswerMatched() throws Exception {
        EndingScreens screens = screensFor(finishedComputerGame(true));

        reveal(screens, "You won");

        assertEquals(List.of(true), trustworthy);
    }

    @Test
    void doesNotTrustAComputerGameWithAMismatchedAnswer() throws Exception {
        EndingScreens screens = screensFor(finishedComputerGame(false));

        reveal(screens, "You won");

        assertEquals(List.of(false), trustworthy,
                "An answer that does not match the committed character must not be stored");
    }

    @Test
    void explainsWhichAnswersDidNotMatch() throws Exception {
        EndingScreens screens = screensFor(finishedComputerGame(false));

        reveal(screens, "You won");

        String shown = labelText(screens.panel());
        assertTrue(shown.contains("questions wrong"), shown);
        assertTrue(shown.contains("will not be saved"), shown);
    }

    @Test
    void revealsWithoutAskingAnythingFurther() throws Exception {
        EndingScreens screens = screensFor(finishedPlayerGame());

        reveal(screens, "Alex won");

        assertEquals(1, trustworthy.size(),
                "Both characters were chosen before play, so there is nothing left to ask");
        assertTrue(labelText(screens.panel()).contains("Alex won"));
    }

    @Test
    void aTwoPlayerGameIsAlwaysTrusted() throws Exception {
        EndingScreens screens = screensFor(finishedPlayerGame());

        reveal(screens, "Alex won");

        assertFalse(trustworthy.isEmpty());
        assertTrue(trustworthy.get(0),
                "There is no computer transcript to check a two-player game against");
    }

    @Test
    void namesWhoseCharacterIsWhose() throws Exception {
        EndingScreens screens = screensFor(finishedPlayerGame());

        reveal(screens, "Alex won");

        String shown = labelText(screens.panel());
        assertTrue(shown.contains("Alex"), shown);
        assertTrue(shown.contains("Blake"), shown);
    }

    @Test
    void offersAnotherGame() throws Exception {
        EndingScreens screens = screensFor(finishedPlayerGame());
        reveal(screens, "Alex won");

        SwingUtilities.invokeAndWait(findButton(screens.panel(), "Play again")::doClick);

        assertEquals(List.of("again"), rematches);
    }

    @Test
    void showsOneResultAtATime() throws Exception {
        EndingScreens screens = screensFor(finishedComputerGame(false));
        reveal(screens, "You won");
        String first = labelText(screens.panel());

        reveal(screens, "You won");

        assertEquals(first, labelText(screens.panel()),
                "A second game must replace the previous reveal, not add to it");
    }

    private JButton findButton(Container container, String label) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && label.equals(button.getText())) {
                return button;
            }
            if (child instanceof JPanel nested) {
                JButton found = findButton(nested, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // --- helpers -------------------------------------------------------

    private EndingScreens screensFor(GameController controller) throws Exception {
        AtomicReference<EndingScreens> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                new EndingScreens(controller, images, trustworthy::add,
                        () -> rematches.add("again"))));
        return reference.get();
    }

    private void reveal(EndingScreens screens, String outcome) throws Exception {
        SwingUtilities.invokeAndWait(() -> screens.begin(outcome));
    }

    /** Plays one computer game, answering either truthfully or not, then finishes it. */
    private GameController finishedComputerGame(boolean answerTruthfully) throws Exception {
        GameSetup setup = new GameSetup();
        setup.againstComputer(ComputerDifficulty.HARD, QuestionMode.PRESET);
        setup.firstUsername("Alex");
        GameController controller = new GameController(new Game(), setup);
        controller.start(OpeningTurn.COMPUTER);
        controller.game().selectCharacter("Alex", "Sam");

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
        controller.game().selectCharacter("Alex", "Sam");
        controller.game().selectCharacter("Blake", "Olivia");
        controller.game().finish("Alex");
        return controller;
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
}
