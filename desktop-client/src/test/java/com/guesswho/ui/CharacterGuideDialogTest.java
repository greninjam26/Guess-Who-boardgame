package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.guesswho.game.Board;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class CharacterGuideDialogTest {
    @Test
    void listsEveryCharacterWithTheAttributesUsedByTheGame() throws Exception {
        JTable table = table();

        assertEquals(24, table.getRowCount());
        assertEquals(11, table.getColumnCount());
        assertEquals("Name", table.getColumnName(0));
        assertEquals("Eye colour", table.getColumnName(1));
        assertEquals("Sam", table.getValueAt(0, 0));
        assertEquals("Green", table.getValueAt(0, 1));
        assertEquals("Male", table.getValueAt(0, 2));
        assertEquals("Dark", table.getValueAt(0, 3));
        assertEquals("Black", table.getValueAt(0, 4));
        assertEquals("No", table.getValueAt(0, 5));
        assertEquals("No", table.getValueAt(0, 6));
        assertEquals("No", table.getValueAt(0, 7));
        assertEquals("Yes", table.getValueAt(0, 8));
        assertEquals("Short", table.getValueAt(0, 9));
        assertEquals("No", table.getValueAt(0, 10));
    }

    @Test
    void isAReadOnlyReference() throws Exception {
        assertFalse(table().isCellEditable(0, 0));
    }

    private JTable table() throws Exception {
        AtomicReference<JTable> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                reference.set(CharacterGuideDialog.table(new Board()));
            }
            catch (Exception unloadable) {
                throw new IllegalStateException(unloadable);
            }
        });
        return reference.get();
    }
}
