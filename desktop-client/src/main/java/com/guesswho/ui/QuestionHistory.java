package com.guesswho.ui;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The running transcript shown down either side of the board: what each
 * participant asked and the answer they were given.
 *
 * <p>Each side keeps its text and its label together. They used to be separate
 * fields, appended to in one statement and pushed to the label in the next, in
 * six different places — so a caller that forgot the second statement would
 * silently drop an entry from the display while the text carried on growing.</p>
 *
 * <p>Entries are HTML because a {@code JLabel} has no other way to break a
 * line, so anything written here that came from a player goes through
 * {@link LabelText#escaped} first.</p>
 */
class QuestionHistory {
    private final Side first = new Side();
    private final Side second = new Side();

    /**
     * Names the participants and clears both transcripts.
     *
     * @param firstName name shown above the first transcript
     * @param secondName name shown above the second transcript
     */
    void begin(String firstName, String secondName) {
        first.begin(firstName);
        second.begin(secondName);
    }

    /**
     * Adds an entry to the first participant's transcript.
     *
     * @param entry text to append, already escaped
     */
    void recordForFirst(String entry) {
        first.append(entry);
    }

    /**
     * Adds an entry to the second participant's transcript.
     *
     * @param entry text to append, already escaped
     */
    void recordForSecond(String entry) {
        second.append(entry);
    }

    /**
     * Returns the panel for the first participant's transcript.
     *
     * @return the first transcript panel
     */
    JPanel firstPanel() {
        return first.panel;
    }

    /**
     * Returns the panel for the second participant's transcript.
     *
     * @return the second transcript panel
     */
    JPanel secondPanel() {
        return second.panel;
    }

    /**
     * Returns the first transcript as displayed, for tests.
     *
     * @return the rendered first transcript
     */
    String firstText() {
        return first.label.getText();
    }

    /**
     * Returns the second transcript as displayed, for tests.
     *
     * @return the rendered second transcript
     */
    String secondText() {
        return second.label.getText();
    }

    /**
     * The transcripts as stored, for saving a game.
     *
     * @return the first player's entries, without the surrounding markup
     */
    String firstEntries() {
        return first.entries.toString();
    }

    /**
     * @return the second player's entries, without the surrounding markup
     */
    String secondEntries() {
        return second.entries.toString();
    }

    /**
     * Puts both transcripts back as a saved game left them.
     *
     * @param firstSaved  the first player's entries
     * @param secondSaved the second player's entries
     */
    void restore(String firstSaved, String secondSaved) {
        first.restore(firstSaved);
        second.restore(secondSaved);
    }

    private static final class Side {
        private final JPanel panel = new JPanel();
        private final JLabel label = new JLabel();
        private final StringBuilder entries = new StringBuilder();

        private Side() {
            panel.add(label);
            panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        }

        private void begin(String name) {
            entries.setLength(0);
            entries.append(LabelText.escaped(name)).append(": <br>");
            render();
        }

        private void append(String entry) {
            entries.append(entry).append("<br>");
            render();
        }

        private void restore(String saved) {
            entries.setLength(0);
            entries.append(saved == null ? "" : saved);
            render();
        }

        private void render() {
            label.setText("<html>" + entries + "</html>");
        }
    }
}
