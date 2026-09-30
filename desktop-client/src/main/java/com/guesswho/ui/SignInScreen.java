package com.guesswho.ui;

import com.guesswho.client.AccountClient;
import java.awt.CardLayout;
import java.awt.Component;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Signing in, creating an account, or doing neither.
 *
 * <p><b>Play as a guest</b> is on the first screen and not hidden behind
 * anything. The entire game works without an account; signing in buys a
 * leaderboard row that belongs to you rather than to whoever typed your name,
 * and that is worth offering rather than demanding.</p>
 */
class SignInScreen {
    /** Told when the player is ready to move on, signed in or not. */
    @FunctionalInterface
    interface Completion {
        /** Called once the player has signed in or chosen to play as a guest. */
        void signInComplete();
    }

    private static final String CHOICE = "choice";
    private static final String SIGN_IN = "signIn";
    private static final String REGISTER = "register";

    private final AccountClient accounts;
    private final PlayerIdentity identity;
    private final Completion completion;

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final JTextField signInUsername = new JTextField(18);
    private final JPasswordField signInPassword = new JPasswordField(18);
    private final JTextField registerUsername = new JTextField(18);
    private final JPasswordField registerPassword = new JPasswordField(18);

    private final JLabel signInMessage = new JLabel(" ");
    private final JLabel registerMessage = new JLabel(" ");

    /**
     * @param accounts   talks to the server
     * @param identity   remembers who signed in
     * @param completion notified when the player is ready to play
     */
    SignInScreen(AccountClient accounts, PlayerIdentity identity, Completion completion) {
        this.accounts = accounts;
        this.identity = identity;
        this.completion = completion;

        root.add(choiceCard(), CHOICE);
        root.add(credentialsCard("Sign in",
                "Use your account to keep your leaderboard progress.",
                signInUsername, signInPassword, signInMessage, this::signIn), SIGN_IN);
        root.add(credentialsCard("Create an account",
                "Choose an account for saving your leaderboard progress.",
                registerUsername, registerPassword, registerMessage, this::register), REGISTER);
    }

    /**
     * @return the panel holding the sign-in screens
     */
    JPanel panel() {
        return root;
    }

    private JPanel choiceCard() {
        JPanel panel = UiTheme.screen();
        JPanel card = UiTheme.card();

        card.add(UiTheme.title("Welcome to Guess Who?"));
        card.add(UiTheme.gap(8));
        card.add(UiTheme.subtitle(
                "Sign in to save your leaderboard progress, or continue as a guest."));
        card.add(UiTheme.gap(24));

        JButton signIn = UiTheme.primaryButton("Sign in");
        JButton register = UiTheme.choiceButton("Create an account");
        JButton guest = UiTheme.choiceButton("Play as a guest");
        signIn.addActionListener(event -> show(SIGN_IN));
        register.addActionListener(event -> show(REGISTER));
        //No confirmation and no warning. It is a supported way to play.
        guest.addActionListener(event -> completion.signInComplete());

        UiTheme.styleChoiceButton(signIn);
        card.add(signIn);
        card.add(UiTheme.gap(10));
        card.add(register);
        card.add(UiTheme.gap(10));
        card.add(guest);
        UiTheme.addCentered(panel, card);
        return panel;
    }

    private JPanel credentialsCard(String title, String subtitle, JTextField username,
            JPasswordField password, JLabel message, Runnable submit) {
        JPanel panel = UiTheme.screen();
        JPanel card = UiTheme.card();
        card.add(UiTheme.title(title));
        card.add(UiTheme.gap(8));
        card.add(UiTheme.subtitle(subtitle));
        card.add(UiTheme.gap(24));

        UiTheme.styleInput(username);
        UiTheme.styleInput(password);
        card.add(fieldGroup("Username", username));
        card.add(UiTheme.gap(14));
        card.add(fieldGroup("Password", password));
        card.add(UiTheme.gap(14));

        JButton go = UiTheme.primaryButton(title);
        JButton back = UiTheme.choiceButton("Back");
        UiTheme.styleChoiceButton(go);
        go.addActionListener(event -> submit.run());
        back.addActionListener(event -> show(CHOICE));
        //Enter submits, because a password field is the one place people expect
        //it to and reaching for the mouse there feels broken.
        password.addActionListener(event -> submit.run());

        message.setForeground(UiTheme.ERROR);
        UiTheme.alignCenter(message);
        card.add(message);
        card.add(UiTheme.gap(14));
        card.add(go);
        card.add(UiTheme.gap(10));
        card.add(back);
        UiTheme.addCentered(panel, card);
        return panel;
    }

    private JPanel fieldGroup(String text, JTextField field) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setBackground(UiTheme.SURFACE);
        group.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel label = new JLabel(text);
        label.setForeground(UiTheme.TEXT);
        label.setLabelFor(field);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(label);
        group.add(UiTheme.gap(6));
        group.add(field);
        return group;
    }

    private void signIn() {
        String username = signInUsername.getText().trim();
        String password = new String(signInPassword.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            showError(signInMessage, "Enter a username and password");
            return;
        }
        showProgress(signInMessage, "Signing in...");
        accounts.logIn(username, password).thenAccept(outcome ->
                SwingUtilities.invokeLater(() -> completeSignIn(outcome, signInMessage)));
    }

    private void register() {
        String username = registerUsername.getText().trim();
        String password = new String(registerPassword.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            showError(registerMessage, "Choose a username and password");
            return;
        }
        showProgress(registerMessage, "Creating your account...");
        accounts.register(username, password).thenAccept(outcome ->
                SwingUtilities.invokeLater(() -> completeRegistration(outcome, password)));
    }

    private void completeRegistration(AccountClient.Outcome outcome, String password) {
        if (outcome.kind() != AccountClient.Outcome.Kind.REGISTERED) {
            showError(registerMessage, outcome.message());
            return;
        }
        //Registering and then being asked to type the same thing again is a
        //pointless step, so the account that was just created is signed into.
        showProgress(registerMessage, "Signing you in...");
        accounts.logIn(outcome.account().username(), password).thenAccept(signIn ->
                SwingUtilities.invokeLater(() -> completeSignIn(signIn, registerMessage)));
    }

    private void completeSignIn(AccountClient.Outcome outcome, JLabel message) {
        if (!outcome.isLoggedIn()) {
            showError(message, outcome.message());
            return;
        }
        identity.signedIn(outcome);
        clearPasswords();
        completion.signInComplete();
    }

    /** Nothing keeps a typed password around after it has been used. */
    private void clearPasswords() {
        signInPassword.setText("");
        registerPassword.setText("");
    }

    private void show(String card) {
        signInMessage.setText(" ");
        registerMessage.setText(" ");
        cards.show(root, card);
    }

    private void showProgress(JLabel message, String text) {
        message.setForeground(UiTheme.MUTED_TEXT);
        message.setText(text);
    }

    private void showError(JLabel message, String text) {
        message.setForeground(UiTheme.ERROR);
        message.setText(text);
    }
}
