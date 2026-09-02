package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void fadesTheCharactersAlreadyRuledOutOnASelectingBoard() throws Exception {
        //The bug this exists for: pressing Guess swapped the tracking board for
        //a clean one, so a game's worth of eliminating vanished at the moment it
        //decided the guess.
        CharacterBoard board = selecting();

        SwingUtilities.invokeAndWait(() -> board.showRuledOut(
                flags(3, 9, 20)));

        assertSame(images.ruledOut(3), cards(board).get(3).getIcon());
        assertSame(images.ruledOut(9), cards(board).get(9).getIcon());
        assertSame(images.portrait(4), cards(board).get(4).getIcon(),
                "A character nobody ruled out should be shown normally");
    }

    @Test
    void keepsARuledOutCharacterPickable() throws Exception {
        //Faded, not disabled. A player who eliminated somebody by mistake has to
        //be able to guess them anyway — the tracking board lets a flip be undone
        //for the same reason, and this is the one place the mistake would stick.
        List<Integer> chosen = new ArrayList<>();
        AtomicReference<CharacterBoard> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                reference.set(CharacterBoard.selecting(images, chosen::add)));
        CharacterBoard board = reference.get();
        SwingUtilities.invokeAndWait(() -> board.showRuledOut(flags(6)));

        click(board, 6);

        assertEquals(List.of(6), chosen);
    }

    @Test
    void showsRuledOutCharactersDifferentlyFromEliminatedOnes() throws Exception {
        //The eliminated card hides the face, which is right on a tracking board
        //and wrong here: you cannot pick a character you can no longer see.
        CharacterBoard board = selecting();

        SwingUtilities.invokeAndWait(() -> board.showRuledOut(flags(1)));

        assertNotSame(images.eliminated(), cards(board).get(1).getIcon());
    }

    @Test
    void putsEveryPortraitBackWhenTheBoardIsReset() throws Exception {
        CharacterBoard board = selecting();
        SwingUtilities.invokeAndWait(() -> board.showRuledOut(flags(0, 5)));

        SwingUtilities.invokeAndWait(board::reset);

        assertSame(images.portrait(0), cards(board).get(0).getIcon());
        assertSame(images.portrait(5), cards(board).get(5).getIcon());
    }

    @Test
    void refusesToFadeATrackingBoard() throws Exception {
        //A tracking board renders its own flips as face-down cards. Fading it
        //too would overwrite those with faded portraits, turning a player's
        //eliminations back into visible faces on the board they use to track
        //them.
        CharacterBoard board = tracking();

        assertThrows(IllegalStateException.class, () -> board.showRuledOut(flags(2)));
    }

    /** One flag per board position, true for the positions named. */
    private static List<Boolean> flags(int... ruledOut) {
        List<Boolean> flags = new ArrayList<>();
        for (int index = 0; index < CharacterBoard.CHARACTER_COUNT; index++) {
            flags.add(false);
        }
        for (int index : ruledOut) {
            flags.set(index, true);
        }
        return flags;
    }

    private static CharacterBoard selecting() throws Exception {
        AtomicReference<CharacterBoard> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                reference.set(CharacterBoard.selecting(images, index -> {
                })));
        return reference.get();
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
