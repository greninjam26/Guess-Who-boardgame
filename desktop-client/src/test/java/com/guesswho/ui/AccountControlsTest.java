package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AccountControlsTest {
    @Test
    void guestCanOpenTheAccountScreenFromSetup() throws Exception {
        List<String> actions = new ArrayList<>();
        AccountControls controls = controls(actions);

        SwingUtilities.invokeAndWait(() -> controls.show(Optional.empty()));

        assertEquals("Playing as guest", label(controls).getText());
        assertEquals("Sign in / Create account", button(controls).getText());
        assertTrue(button(controls).isEnabled());

        SwingUtilities.invokeAndWait(button(controls)::doClick);
        assertEquals(List.of("sign-in"), actions);
    }

    @Test
    void signedInPlayerCanSignOutFromSetup() throws Exception {
        List<String> actions = new ArrayList<>();
        AccountControls controls = controls(actions);

        SwingUtilities.invokeAndWait(() -> controls.show(Optional.of("Misty")));

        assertEquals("Signed in as Misty", label(controls).getText());
        assertEquals("Sign out", button(controls).getText());

        SwingUtilities.invokeAndWait(button(controls)::doClick);
        assertEquals(List.of("sign-out"), actions);
    }

    @Test
    void gameKeepsAccountStatusVisibleButPreventsSwitching() throws Exception {
        AccountControls controls = controls(new ArrayList<>());

        SwingUtilities.invokeAndWait(() -> {
            controls.show(Optional.of("Misty"));
            controls.switchingAllowed(false);
        });

        assertEquals("Signed in as Misty", label(controls).getText());
        assertFalse(button(controls).isEnabled());
    }

    private AccountControls controls(List<String> actions) throws Exception {
        AtomicReference<AccountControls> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(new AccountControls(
                () -> actions.add("sign-in"),
                () -> actions.add("sign-out"))));
        return reference.get();
    }

    private JLabel label(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JLabel label) {
                return label;
            }
        }
        throw new AssertionError("No account status label");
    }

    private JButton button(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JButton button) {
                return button;
            }
        }
        throw new AssertionError("No account action button");
    }
}
