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
