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
    /** True on a board whose cards a player turns over for themselves. */
    private boolean tracksFlips;
    private Runnable onFlip = () -> {
    };

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
        return tracking(images, () -> {
        });
    }

    /**
     * Creates a tracking board that reports when a player turns a card over.
     *
     * @param images portraits shown on the cards
     * @param onFlip run after a player flips a card, so the game can be saved
     * @return a board whose cards flip face down and back
     */
    static CharacterBoard tracking(CharacterImages images, Runnable onFlip) {
        CharacterBoard board = new CharacterBoard(images, index -> {
        });
        board.onFlip = onFlip;
        board.tracksFlips = true;
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

    /** A player turning a card over, which is worth saving. */
    private void flip(int characterIndex) {
        setFaceDown(characterIndex, !faceDown[characterIndex]);
        onFlip.run();
    }

    /**
     * Turns one card, without treating it as something the player just did.
     *
     * <p>Resetting and restoring both move cards, and neither is a move worth
     * saving: one is starting a new game, the other is putting a saved one
     * back.</p>
     */
    private void setFaceDown(int characterIndex, boolean down) {
        faceDown[characterIndex] = down;
        cards.get(characterIndex).setIcon(down
                ? images.eliminated()
                : images.portrait(characterIndex));
    }

    /**
     * Turns every card face up again, for a new game on the same board.
     */
    void reset() {
        //Restores every portrait, which clears any fading a selecting board was
        //showing as well as turning a tracking board's cards back over.
        for (int index = 0; index < CHARACTER_COUNT; index++) {
            setFaceDown(index, false);
        }
    }

    /**
     * Fades the characters a player has already ruled out.
     *
     * <p>For a selecting board, which is otherwise twenty-four faces with
     * nothing to say which of them the player spent the whole game
     * eliminating. Their own working notes are on the tracking board they were
     * just looking at, and dropping that at the moment of guessing asks them to
     * remember it instead.</p>
     *
     * <p>Faded, not removed, and still clickable. A player who ruled somebody
     * out by mistake has to be able to pick them anyway — the tracking board
     * lets a flip be undone for the same reason, and a guess board that refused
     * would be the one place the mistake became final.</p>
     *
     * <p>Refused on a tracking board, and not as a matter of taste. Fading is a
     * reading of the flips, and a board that both records them and renders that
     * reading would overwrite its own face-down cards with faded portraits the
     * next time anything asked it to redraw. The two jobs stay on the two kinds
     * of board.</p>
     *
     * @param flipped one flag per board position; shorter or empty leaves the
     *                rest showing normally
     * @throws IllegalStateException if called on a tracking board
     */
    void showRuledOut(List<Boolean> flipped) {
        if (tracksFlips) {
            throw new IllegalStateException(
                    "A tracking board shows its own flips; fading is for a board being picked from");
        }
        for (int index = 0; index < CHARACTER_COUNT; index++) {
            boolean out = index < flipped.size() && Boolean.TRUE.equals(flipped.get(index));
            cards.get(index).setIcon(
                    out ? images.ruledOut(index) : images.portrait(index));
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
     * <p>Sets each card through the same method a flip uses, so the pictures
     * follow. Assigning the flags alone would restore a board that knew which
     * cards were down but did not show it.</p>
     *
     * @param flipped one flag per board position
     */
    void restore(List<Boolean> flipped) {
        reset();
        for (int index = 0; index < CHARACTER_COUNT && index < flipped.size(); index++) {
            setFaceDown(index, Boolean.TRUE.equals(flipped.get(index)));
        }
    }
}
