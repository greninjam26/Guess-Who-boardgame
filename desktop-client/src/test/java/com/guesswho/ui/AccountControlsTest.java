package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Color;
import java.awt.Font;
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
    void accountStateStaysProminentAndReadable() throws Exception {
        AccountControls controls = controls(new ArrayList<>());

        SwingUtilities.invokeAndWait(() -> controls.show(Optional.of("Misty")));

        JLabel status = label(controls);
        JButton action = button(controls);
        assertTrue((status.getFont().getStyle() & Font.BOLD) != 0,
                "The current account should be visually prominent");
        assertTrue(contrast(status.getForeground(), controls.getBackground()) >= 4.5,
                "Account status text should remain readable");
        assertTrue(action.getPreferredSize().height >= 36,
                "Account actions should be easy to target");
    }

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

    private double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private double luminance(Color color) {
        return 0.2126 * channel(color.getRed())
                + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private double channel(int value) {
        double ratio = value / 255.0;
        return ratio <= 0.04045
                ? ratio / 12.92
                : Math.pow((ratio + 0.055) / 1.055, 2.4);
    }
}
