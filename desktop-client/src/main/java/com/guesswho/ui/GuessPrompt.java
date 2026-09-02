package com.guesswho.ui;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * The line above a board somebody is about to guess from.
 *
 * <p>Guessing swaps one board of twenty-four faces for another that looks
 * almost the same. Without a word of explanation the swap reads as the game
 * having lost the player's eliminations rather than as a prompt to pick
 * somebody — and a player who has flipped nothing yet is left looking at
 * twenty-four identical choices with no idea what the screen wants.</p>
 *
 * <p>Shared by both modes because the confusion is identical in both, and
 * because two copies of a sentence drift.</p>
 */
final class GuessPrompt {
    private GuessPrompt() {
    }

    /**
     * @return the prompt shown above a guess board
     */
    static JLabel label() {
        JLabel prompt = new JLabel(
                "Who is your opponent holding? Click their card to guess. "
                        + "Faded characters are ones you ruled out — pick one "
                        + "anyway if you have changed your mind.",
                SwingConstants.CENTER);
        return prompt;
    }
}
