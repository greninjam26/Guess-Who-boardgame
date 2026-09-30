package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.account.Account;
import com.guesswho.client.AccountClient;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SignInScreenTest {
    @Test
    void accountChoiceHasAReadableVisualHierarchy() throws Exception {
        SignInScreen screen = screen();
        JPanel choice = visibleCard(screen.panel());
        List<JLabel> labels = labels(choice);

        assertEquals("Welcome to Guess Who?", labels.get(0).getText());
        assertEquals("Sign in to save your leaderboard progress, or continue as a guest.",
                labels.get(1).getText());
        assertTrue(labels.get(0).getFont().getSize2D()
                        > labels.get(1).getFont().getSize2D(),
                "The account heading should be more prominent than supporting copy");

        List<JButton> actions = buttons(choice);
        assertEquals(List.of("Sign in", "Create an account", "Play as a guest"),
                actions.stream().map(JButton::getText).toList());
        assertTrue(actions.stream().allMatch(button -> button.getPreferredSize().height >= 40),
                "Every account choice should be easy to target");
        assertEquals(1, actions.stream().map(button -> button.getPreferredSize().width)
                .distinct().count(), "Account choices should form one aligned column");
        JButton signIn = actions.get(0);
        assertTrue(contrast(signIn.getForeground(), signIn.getBackground()) >= 4.5,
                "The primary account action should remain readable");
    }

    @Test
    void signInFormIsClearAccessibleAndReadable() throws Exception {
        SignInScreen screen = screen();
        click(screen.panel(), "Sign in");
        JPanel form = visibleCard(screen.panel());

        List<JLabel> labels = labels(form);
        assertEquals("Sign in", labels.get(0).getText());
        assertEquals("Use your account to keep your leaderboard progress.",
                labels.get(1).getText());

        JTextField username = fields(form).stream()
                .filter(field -> !(field instanceof JPasswordField))
                .findFirst().orElseThrow();
        JPasswordField password = fields(form).stream()
                .filter(JPasswordField.class::isInstance)
                .map(JPasswordField.class::cast)
                .findFirst().orElseThrow();
        assertSame(username, findLabel(form, "Username").getLabelFor());
        assertSame(password, findLabel(form, "Password").getLabelFor());
        assertTrue(username.getPreferredSize().height >= 36);
        assertTrue(password.getPreferredSize().height >= 36);

        JButton back = findButton(form, "Back");
        JButton submit = findButton(form, "Sign in");
        assertTrue(back.getPreferredSize().height >= 40);
        assertTrue(submit.getPreferredSize().height >= 40);
        assertTrue(contrast(submit.getForeground(), submit.getBackground()) >= 4.5);

        SwingUtilities.invokeAndWait(submit::doClick);
        JLabel message = findLabel(form, "Enter a username and password");
        assertTrue(contrast(message.getForeground(), UiTheme.SURFACE) >= 4.5,
                "Validation messages should remain readable");
    }

    @Test
    void signInShowsAReadableProgressMessageWhileWaiting() throws Exception {
        CompletableFuture<AccountClient.Outcome> response = new CompletableFuture<>();
        SignInScreen screen = screen(serverWaitingFor(response));
        click(screen.panel(), "Sign in");
        JPanel form = visibleCard(screen.panel());
        List<JTextField> fields = fields(form);
        SwingUtilities.invokeAndWait(() -> {
            fields.get(0).setText("Misty");
            fields.get(1).setText("password");
        });

        SwingUtilities.invokeAndWait(findButton(form, "Sign in")::doClick);

        JLabel progress = findLabel(form, "Signing in...");
        assertEquals(UiTheme.MUTED_TEXT, progress.getForeground());
        assertTrue(contrast(progress.getForeground(), UiTheme.SURFACE) >= 4.5);
    }

    private SignInScreen screen() throws Exception {
        return screen(null);
    }

    private SignInScreen screen(AccountClient accounts) throws Exception {
        AtomicReference<SignInScreen> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(
                new SignInScreen(accounts, null, () -> { })));
        return reference.get();
    }

    private AccountClient serverWaitingFor(CompletableFuture<AccountClient.Outcome> response) {
        return new AccountClient() {
            @Override
            public CompletableFuture<Outcome> register(String username, String password) {
                return response;
            }

            @Override
            public CompletableFuture<Outcome> logIn(String username, String password) {
                return response;
            }

            @Override
            public CompletableFuture<Optional<Account>> whoAmI(String token) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletableFuture<Void> logOut(String token) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    private JPanel visibleCard(Container root) {
        for (Component child : root.getComponents()) {
            if (child.isVisible() && child instanceof JPanel panel) {
                return panel;
            }
        }
        throw new AssertionError("No card is showing");
    }

    private List<JLabel> labels(Container container) {
        List<JLabel> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel label) {
                found.add(label);
            }
            else if (child instanceof Container nested) {
                found.addAll(labels(nested));
            }
        }
        return found;
    }

    private List<JButton> buttons(Container container) {
        List<JButton> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button) {
                found.add(button);
            }
            else if (child instanceof Container nested) {
                found.addAll(buttons(nested));
            }
        }
        return found;
    }

    private List<JTextField> fields(Container container) {
        List<JTextField> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof JTextField field) {
                found.add(field);
            }
            else if (child instanceof Container nested) {
                found.addAll(fields(nested));
            }
        }
        return found;
    }

    private void click(Container container, String text) throws Exception {
        SwingUtilities.invokeAndWait(findButton(container, text)::doClick);
    }

    private JButton findButton(Container container, String text) {
        return buttons(container).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No button labelled " + text));
    }

    private JLabel findLabel(Container container, String text) {
        return labels(container).stream()
                .filter(label -> text.equals(label.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No label reading " + text));
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
