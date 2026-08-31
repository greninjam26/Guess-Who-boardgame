package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CharacterBoardTest {
    private static CharacterImages images;

    @BeforeAll
    static void loadImages() throws Exception {
        AtomicReference<CharacterImages> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new CharacterImages()));
        images = reference.get();
    }

    @Test
    void showsEveryCharacter() throws Exception {
        CharacterBoard board = tracking();

        assertEquals(CharacterBoard.CHARACTER_COUNT, cards(board).size());
    }

    @Test
    void startsWithEveryCharacterFaceUp() throws Exception {
        CharacterBoard board = tracking();

        for (int index = 0; index < CharacterBoard.CHARACTER_COUNT; index++) {
            assertFalse(board.isFaceDown(index));
            assertSame(images.portrait(index), cards(board).get(index).getIcon());
        }
    }

    @Test
    void flipsACharacterFaceDownWhenClicked() throws Exception {
        CharacterBoard board = tracking();

        click(board, 7);

        assertTrue(board.isFaceDown(7));
        assertSame(images.eliminated(), cards(board).get(7).getIcon());
    }

    @Test
    void flipsACharacterBackWhenClickedAgain() throws Exception {
        CharacterBoard board = tracking();
        click(board, 7);

        click(board, 7);

        assertFalse(board.isFaceDown(7),
                "A mistaken elimination has to be undoable");
        assertSame(images.portrait(7), cards(board).get(7).getIcon());
    }

    @Test
    void flipsOnlyTheCharacterClicked() throws Exception {
        CharacterBoard board = tracking();

        click(board, 3);

        assertTrue(board.isFaceDown(3));
        assertFalse(board.isFaceDown(2));
        assertFalse(board.isFaceDown(4));
    }

    @Test
    void twoTrackingBoardsKeepSeparateState() throws Exception {
        CharacterBoard first = tracking();
        CharacterBoard second = tracking();

        click(first, 5);

        assertTrue(first.isFaceDown(5));
        assertFalse(second.isFaceDown(5), "Each player tracks their own board");
    }

    @Test
    void aSelectingBoardReportsTheCharacterClicked() throws Exception {
        List<Integer> chosen = new ArrayList<>();
        AtomicReference<CharacterBoard> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                reference.set(CharacterBoard.selecting(images, chosen::add)));

        click(reference.get(), 11);

        assertEquals(List.of(11), chosen);
    }

    @Test
    void aSelectingBoardDoesNotFlipWhatItReports() throws Exception {
        AtomicReference<CharacterBoard> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                reference.set(CharacterBoard.selecting(images, index -> {
                })));
        CharacterBoard board = reference.get();

        click(board, 2);

        assertFalse(board.isFaceDown(2));
        assertNotSame(images.eliminated(), cards(board).get(2).getIcon());
    }

    @Test
    void turnsEveryCardBackUpForANewGame() throws Exception {
        CharacterBoard board = tracking();
        click(board, 3);
        click(board, 17);

        SwingUtilities.invokeAndWait(board::reset);

        for (int index = 0; index < CharacterBoard.CHARACTER_COUNT; index++) {
            assertFalse(board.isFaceDown(index),
                    "A rematch on the same board would start with characters ruled out");
            assertSame(images.portrait(index), cards(board).get(index).getIcon());
        }
    }

    @Test
    void reportsACardTheUserTurnedOver() throws Exception {
        List<String> flips = new ArrayList<>();
        CharacterBoard board = tracking(() -> flips.add("flipped"));

        SwingUtilities.invokeAndWait(() -> cards(board).get(4).doClick());

        assertEquals(1, flips.size(),
                "A flipped card is a note the player made and the game should be saved");
    }

    @Test
    void saysNothingWhileACardIsBeingPutBack() throws Exception {
        List<String> flips = new ArrayList<>();
        CharacterBoard board = tracking(() -> flips.add("flipped"));

        SwingUtilities.invokeAndWait(() -> board.restore(faceDown(1, 2, 3)));

        assertTrue(flips.isEmpty(),
                "Restoring a saved game is not the player making twenty-four moves");
    }

    @Test
    void saysNothingWhileTheBoardIsBeingCleared() throws Exception {
        List<String> flips = new ArrayList<>();
        CharacterBoard board = tracking(() -> flips.add("flipped"));
        SwingUtilities.invokeAndWait(() -> board.restore(faceDown(0)));
        flips.clear();

        SwingUtilities.invokeAndWait(board::reset);

        assertTrue(flips.isEmpty());
    }

    @Test
    void putsTheCardsAndTheirPicturesBackTogether() throws Exception {
        CharacterBoard board = tracking();

        SwingUtilities.invokeAndWait(() -> board.restore(faceDown(2, 7)));

        assertTrue(board.isFaceDown(2));
        assertTrue(board.isFaceDown(7));
        assertFalse(board.isFaceDown(3));
        assertSame(images.eliminated(), cards(board).get(2).getIcon(),
                "A restored board that knows a card is down but still shows the face is worse "
                        + "than one that forgot");
        assertSame(images.portrait(3), cards(board).get(3).getIcon());
    }

    @Test
    void handsBackWhichCardsAreDown() throws Exception {
        CharacterBoard board = tracking();

        SwingUtilities.invokeAndWait(() -> board.restore(faceDown(5)));

        assertEquals(faceDown(5), board.faceDownCards());
    }

    @Test
    void survivesASaveThatIsShorterThanTheBoard() throws Exception {
        CharacterBoard board = tracking();

        SwingUtilities.invokeAndWait(() -> board.restore(List.of(true, true)));

        assertTrue(board.isFaceDown(0));
        assertFalse(board.isFaceDown(23));
    }

    private static List<Boolean> faceDown(int... flipped) {
        Boolean[] cards = new Boolean[CharacterBoard.CHARACTER_COUNT];
        java.util.Arrays.fill(cards, false);
        for (int index : flipped) {
            cards[index] = true;
        }
        return List.of(cards);
    }

    private CharacterBoard tracking(Runnable onFlip) throws Exception {
        AtomicReference<CharacterBoard> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(CharacterBoard.tracking(images, onFlip)));
        return reference.get();
    }

    private CharacterBoard tracking() throws Exception {
        AtomicReference<CharacterBoard> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(CharacterBoard.tracking(images)));
        return reference.get();
    }

    private void click(CharacterBoard board, int index) throws Exception {
        SwingUtilities.invokeAndWait(cards(board).get(index)::doClick);
    }

    private List<JButton> cards(CharacterBoard board) {
        List<JButton> cards = new ArrayList<>();
        for (Component child : board.getComponents()) {
            if (child instanceof JButton card) {
                cards.add(card);
            }
        }
        return cards;
    }
}
