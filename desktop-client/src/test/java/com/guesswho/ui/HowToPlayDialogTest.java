package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class HowToPlayDialogTest {
    @Test
    void explainsHowATurnWorks() throws Exception {
        String rules = rulesText();

        assertTrue(rules.contains("yes or no"), rules);
        assertTrue(rules.contains("flip"), rules);
    }

    @Test
    void explainsWhatChoosingUpFrontBuys() throws Exception {
        String rules = rulesText();

        assertTrue(rules.contains("keep it to yourself"),
                "A player choosing between the two needs to know what they give up");
        assertTrue(rules.contains("consistent"), rules);
    }

    @Test
    void saysWhyFreeQuestionsAreTwoPlayerOnly() throws Exception {
        assertTrue(rulesText().contains("the computer can only answer the board's"),
                "Otherwise the missing option looks like an oversight");
    }

    @Test
    void isReadOnly() throws Exception {
        assertFalse(pane().isEditable());
    }

    @Test
    void startsAtTheTop() throws Exception {
        assertTrue(pane().getCaretPosition() == 0,
                "A long page scrolled to its end would look empty on opening");
    }

    private String rulesText() throws Exception {
        return SetupText.INSTRUCTIONS.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ");
    }

    private JEditorPane pane() throws Exception {
        AtomicReference<JEditorPane> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(HowToPlayDialog.rules()));
        return reference.get();
    }
}
