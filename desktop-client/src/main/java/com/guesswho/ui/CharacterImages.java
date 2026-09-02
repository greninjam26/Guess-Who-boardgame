package com.guesswho.ui;

import com.guesswho.game.GameResources;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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

    /**
     * How much of a ruled-out portrait still shows.
     *
     * <p>Faint enough that the faces still in play are the ones the eye lands
     * on, solid enough to recognise who it is — a player changing their mind
     * about an elimination has to be able to find them.</p>
     */
    private static final float RULED_OUT_OPACITY = 0.3f;

    private final List<ImageIcon> portraits = new ArrayList<>();
    private final List<ImageIcon> ruledOut = new ArrayList<>();
    private final ImageIcon eliminated;

    /**
     * Loads every portrait and the eliminated-character card.
     *
     * @throws IllegalStateException if an image resource is missing
     */
    CharacterImages() {
        for (int index = 0; index < CharacterBoard.CHARACTER_COUNT; index++) {
            ImageIcon portrait = GameResources.loadCharacterIcon(index, WIDTH, HEIGHT);
            portraits.add(portrait);
            //Faded once here rather than per repaint: twenty-four of these are
            //built at startup and shown on every guess for the rest of the game.
            ruledOut.add(faded(portrait));
        }
        eliminated = GameResources.loadEliminatedCharacterIcon(WIDTH, HEIGHT);
    }

    /**
     * Returns a faded copy of a portrait, for a character already ruled out.
     *
     * <p>Not the eliminated card. That one hides the face, which is right on a
     * tracking board and wrong on a board somebody is picking from — you cannot
     * choose a character you can no longer see.</p>
     *
     * @param index board index of the character
     * @return the portrait, faded
     */
    ImageIcon ruledOut(int index) {
        return ruledOut.get(index);
    }

    private static ImageIcon faded(ImageIcon portrait) {
        BufferedImage canvas = new BufferedImage(
                portrait.getIconWidth(), portrait.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setComposite(
                AlphaComposite.getInstance(AlphaComposite.SRC_OVER, RULED_OUT_OPACITY));
        graphics.drawImage(portrait.getImage(), 0, 0, null);
        graphics.dispose();
        return new ImageIcon(canvas);
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
