package com.guesswho.ui;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * A grid of the twenty-four characters.
 *
 * <p>Named for the interface, not the game: {@code com.guesswho.game.Board}
 * holds the character and question data this one displays.</p>
 *
 * <p>The game shows three of these and they differ only in what a click means.
 * A tracking board is a player's own notes: clicking flips a character face
 * down, and clicking again flips it back, so a mistaken elimination can be
 * undone. A selecting board is used to name a character, and reports the choice
 * instead of changing anything.</p>
 *
 * <p>A tracking board keeps its own flip state. It used to live in a list
 * beside the panel, one per board, which had to be passed back in on every
 * click.</p>
 */
class CharacterBoard extends JPanel {
    /** Characters on a board. */
    static final int CHARACTER_COUNT = 24;
    private static final int COLUMNS = 6;
    private static final int GAP = 10;
    private static final int ROW_GAP = 5;

    /** Notified when a character is chosen on a selecting board. */
    @FunctionalInterface
    interface Selection {
        /**
         * Called when a character is clicked.
         *
         * @param characterIndex board index of the chosen character
         */
        void characterChosen(int characterIndex);
    }

    private final CharacterImages images;
    private final List<JButton> cards = new ArrayList<>();
    private final boolean[] faceDown = new boolean[CHARACTER_COUNT];

    private CharacterBoard(CharacterImages images, Selection selection) {
        //Twenty-four cards in six columns is a grid, so let one lay it out. The
        //cards were previously positioned one by one against a window size that
        //did not fit most screens.
        super(new GridLayout(CHARACTER_COUNT / COLUMNS, COLUMNS, GAP, ROW_GAP));
        this.images = images;
        setBorder(BorderFactory.createEmptyBorder(ROW_GAP, GAP, ROW_GAP, GAP));
        for (int index = 0; index < CHARACTER_COUNT; index++) {
            JButton card = new JButton(images.portrait(index));
            int characterIndex = index;
            card.addActionListener(event -> selection.characterChosen(characterIndex));
            cards.add(card);
            add(card);
        }
    }

    /**
     * Creates a board a player uses to track who they have ruled out.
     *
     * @param images portraits shown on the cards
     * @return a board whose cards flip face down and back
     */
    static CharacterBoard tracking(CharacterImages images) {
        CharacterBoard board = new CharacterBoard(images, index -> {
        });
        board.makeCardsFlip();
        return board;
    }

    /**
     * Creates a board used to name a character.
     *
     * @param images portraits shown on the cards
     * @param selection notified with the chosen character
     * @return a board that reports clicks without changing
     */
    static CharacterBoard selecting(CharacterImages images, Selection selection) {
        return new CharacterBoard(images, selection);
    }

    private void makeCardsFlip() {
        for (int index = 0; index < cards.size(); index++) {
            JButton card = cards.get(index);
            int characterIndex = index;
            card.addActionListener(event -> flip(characterIndex));
        }
    }

    private void flip(int characterIndex) {
        faceDown[characterIndex] = !faceDown[characterIndex];
        cards.get(characterIndex).setIcon(faceDown[characterIndex]
                ? images.eliminated()
                : images.portrait(characterIndex));
    }

    /**
     * Turns every card face up again, for a new game on the same board.
     */
    void reset() {
        for (int index = 0; index < CHARACTER_COUNT; index++) {
            if (faceDown[index]) {
                flip(index);
            }
        }
    }

    /**
     * Reports whether a character has been flipped face down.
     *
     * @param characterIndex board index of the character
     * @return {@code true} when the card is face down
     */
    boolean isFaceDown(int characterIndex) {
        return faceDown[characterIndex];
    }

    /**
     * Which cards are face down, for saving a game in progress.
     *
     * @return one flag per board position, oldest board order
     */
    List<Boolean> faceDownCards() {
        List<Boolean> flipped = new ArrayList<>(CHARACTER_COUNT);
        for (boolean down : faceDown) {
            flipped.add(down);
        }
        return flipped;
    }

    /**
     * Puts the cards back the way a saved game left them.
     *
     * <p>Goes through {@link #flip} rather than assigning the flags, so the
     * pictures follow. Setting the array directly would restore a board that
     * knew which cards were down but did not show it.</p>
     *
     * @param flipped one flag per board position
     */
    void restore(List<Boolean> flipped) {
        reset();
        for (int index = 0; index < CHARACTER_COUNT && index < flipped.size(); index++) {
            if (Boolean.TRUE.equals(flipped.get(index))) {
                flip(index);
            }
        }
    }
}
