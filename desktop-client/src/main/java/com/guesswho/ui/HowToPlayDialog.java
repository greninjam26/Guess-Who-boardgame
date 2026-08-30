package com.guesswho.ui;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * The rules, in a window of their own.
 *
 * <p>They used to be a label revealed inside the welcome screen, which grew it
 * to fit and offered no way to put it away again. A window can be scrolled,
 * closed, and read while the rest of the interface stays where it was.</p>
 */
final class HowToPlayDialog {
    private static final Dimension WINDOW_SIZE = new Dimension(560, 620);

    private HowToPlayDialog() {
    }

    /**
     * Opens the rules.
     *
     * @param besides a component in the window the dialog should belong to
     */
    static void show(Component besides) {
        Window owner = SwingUtilities.getWindowAncestor(besides);
        JDialog dialog = new JDialog(owner, "How To Play", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new JScrollPane(rules()));
        dialog.setPreferredSize(WINDOW_SIZE);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /**
     * Builds the rules pane. Separate from {@link #show} so the text can be
     * checked without a window.
     *
     * @return a read-only pane showing the rules
     */
    static JEditorPane rules() {
        JEditorPane pane = new JEditorPane("text/html", SetupText.INSTRUCTIONS);
        pane.setEditable(false);
        //Otherwise the pane paints its own white behind themed text.
        pane.setOpaque(false);
        pane.setCaretPosition(0);
        return pane;
    }
}
