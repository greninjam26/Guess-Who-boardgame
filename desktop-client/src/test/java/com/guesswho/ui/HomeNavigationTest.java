package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class HomeNavigationTest {
    @Test
    void replacesTheGameInsideTheExistingWindow() throws Exception {
        JPanel contents = panelShowing("game");
        List<String> actions = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> HomeNavigation.returnHome(
                false,
                () -> {
                    actions.add("confirm");
                    return true;
                },
                () -> actions.add("stop"),
                contents,
                () -> contents.add(new JLabel("home"))));

        assertEquals(List.of("stop"), actions);
        assertEquals("home", ((JLabel) contents.getComponent(0)).getText());
    }

    @Test
    void keepsAnActiveGameWhenLeavingIsNotConfirmed() throws Exception {
        JPanel contents = panelShowing("game");
        List<String> actions = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> HomeNavigation.returnHome(
                true,
                () -> {
                    actions.add("confirm");
                    return false;
                },
                () -> actions.add("stop"),
                contents,
                () -> contents.add(new JLabel("home"))));

        assertEquals(List.of("confirm"), actions);
        assertEquals("game", ((JLabel) contents.getComponent(0)).getText());
    }

    @Test
    void leavesAnActiveGameAfterConfirmation() throws Exception {
        JPanel contents = panelShowing("game");
        List<String> actions = new ArrayList<>();

        SwingUtilities.invokeAndWait(() -> HomeNavigation.returnHome(
                true,
                () -> {
                    actions.add("confirm");
                    return true;
                },
                () -> actions.add("stop"),
                contents,
                () -> contents.add(new JLabel("home"))));

        assertEquals(List.of("confirm", "stop"), actions);
        assertEquals("home", ((JLabel) contents.getComponent(0)).getText());
    }

    private JPanel panelShowing(String text) throws Exception {
        AtomicReference<JPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel();
            panel.add(new JLabel(text));
            reference.set(panel);
        });
        return reference.get();
    }
}
