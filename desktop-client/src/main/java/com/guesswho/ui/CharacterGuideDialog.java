package com.guesswho.ui;

import com.guesswho.game.Board;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** A read-only reference to every character attribute used by the game. */
final class CharacterGuideDialog {
    private static final String[] COLUMNS = {
        "Name", "Eye colour", "Gender", "Skin tone", "Hair colour",
        "Facial hair", "Glasses", "Visible teeth", "Hat", "Hair length", "Piercing"
    };

    private CharacterGuideDialog() {
    }

    /** Opens the guide beside the application without interrupting the game. */
    static void show(Component besides) {
        Window owner = besides instanceof Window window
                ? window
                : SwingUtilities.getWindowAncestor(besides);
        try {
            JTable table = table(new Board());
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(980, 480));

            JDialog dialog = new JDialog(
                    owner, "Character Guide", Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setContentPane(scroll);
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }
        catch (Exception unloadable) {
            JOptionPane.showMessageDialog(
                    besides,
                    "The character guide could not be loaded.",
                    "Unable to open character guide",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Builds the table separately so its data can be checked without a window. */
    static JTable table(Board board) {
        DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (com.guesswho.game.Character character : board.getCharacters()) {
            model.addRow(new Object[] {
                character.getName(),
                character.getEyeColour(),
                character.getIsMale() ? "Male" : "Female",
                character.getIsLight() ? "Light" : "Dark",
                character.getHairColour(),
                yesNo(character.getIsFacialHair()),
                yesNo(character.getIsGlasses()),
                yesNo(character.getIsTeethVisible()),
                yesNo(character.getIsHat()),
                character.getHairLength(),
                yesNo(character.getIsPiercing())
            });
        }
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        return table;
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}
