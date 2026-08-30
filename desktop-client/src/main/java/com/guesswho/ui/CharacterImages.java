package com.guesswho.ui;

import com.guesswho.game.GameResources;

import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;

/**
 * The character portraits and the card shown in place of an eliminated one.
 *
 * <p>Loaded and scaled once, then shared by every board, since all three boards
 * show the same twenty-four faces at the same size.</p>
 */
class CharacterImages {
    /** Width of a character card in pixels. */
    static final int WIDTH = 100;
    /** Height of a character card in pixels. */
    static final int HEIGHT = 150;

    private final List<ImageIcon> portraits = new ArrayList<>();
    private final ImageIcon eliminated;

    /**
     * Loads every portrait and the eliminated-character card.
     *
     * @throws IllegalStateException if an image resource is missing
     */
    CharacterImages() {
        for (int index = 0; index < CharacterBoard.CHARACTER_COUNT; index++) {
            portraits.add(GameResources.loadCharacterIcon(index, WIDTH, HEIGHT));
        }
        eliminated = GameResources.loadEliminatedCharacterIcon(WIDTH, HEIGHT);
    }

    /**
     * Returns one character's portrait.
     *
     * @param index board index of the character
     * @return the scaled portrait
     */
    ImageIcon portrait(int index) {
        return portraits.get(index);
    }

    /**
     * Returns the card shown in place of an eliminated character.
     *
     * @return the scaled eliminated-character card
     */
    ImageIcon eliminated() {
        return eliminated;
    }
}
