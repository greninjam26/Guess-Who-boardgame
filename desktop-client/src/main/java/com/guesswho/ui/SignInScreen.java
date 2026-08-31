package com.guesswho.ui;

import com.guesswho.client.AccountClient;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
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
        root.add(credentialsCard("Sign in", signInUsername, signInPassword,
                signInMessage, this::signIn), SIGN_IN);
        root.add(credentialsCard("Create an account", registerUsername, registerPassword,
                registerMessage, this::register), REGISTER);
    }

    /**
     * @return the panel holding the sign-in screens
     */
    JPanel panel() {
        return root;
    }

    private JPanel choiceCard() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        panel.add(new JLabel("Guess Who?"));
        panel.add(new JLabel("Sign in to keep your place on the leaderboard."));

        JButton signIn = new JButton("Sign in");
        JButton register = new JButton("Create an account");
        JButton guest = new JButton("Play as a guest");
        signIn.addActionListener(event -> show(SIGN_IN));
        register.addActionListener(event -> show(REGISTER));
        //No confirmation and no warning. It is a supported way to play.
        guest.addActionListener(event -> completion.signInComplete());

        panel.add(signIn);
        panel.add(register);
        panel.add(guest);
        return panel;
    }

    private JPanel credentialsCard(String title, JTextField username, JPasswordField password,
            JLabel message, Runnable submit) {
        JPanel fields = new JPanel(new GridLayout(0, 2, 6, 6));
        fields.add(new JLabel("Username"));
        fields.add(username);
        fields.add(new JLabel("Password"));
        fields.add(password);

        JButton go = new JButton(title);
        JButton back = new JButton("Back");
        go.addActionListener(event -> submit.run());
        back.addActionListener(event -> show(CHOICE));
        //Enter submits, because a password field is the one place people expect
        //it to and reaching for the mouse there feels broken.
        password.addActionListener(event -> submit.run());

        JPanel buttons = new JPanel();
        buttons.add(back);
        buttons.add(go);

        message.setForeground(Color.RED.darker());

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(fields, BorderLayout.CENTER);
        JPanel foot = new JPanel(new BorderLayout());
        foot.add(message, BorderLayout.NORTH);
        foot.add(buttons, BorderLayout.SOUTH);
        panel.add(foot, BorderLayout.SOUTH);
        return panel;
    }

    private void signIn() {
        String username = signInUsername.getText().trim();
        String password = new String(signInPassword.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            signInMessage.setText("Enter a username and password");
            return;
        }
        signInMessage.setText("Signing in...");
        accounts.logIn(username, password).thenAccept(outcome ->
                SwingUtilities.invokeLater(() -> completeSignIn(outcome, signInMessage)));
    }

    private void register() {
        String username = registerUsername.getText().trim();
        String password = new String(registerPassword.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            registerMessage.setText("Choose a username and password");
            return;
        }
        registerMessage.setText("Creating your account...");
        accounts.register(username, password).thenAccept(outcome ->
                SwingUtilities.invokeLater(() -> completeRegistration(outcome, password)));
    }

    private void completeRegistration(AccountClient.Outcome outcome, String password) {
        if (outcome.kind() != AccountClient.Outcome.Kind.REGISTERED) {
            registerMessage.setText(outcome.message());
            return;
        }
        //Registering and then being asked to type the same thing again is a
        //pointless step, so the account that was just created is signed into.
        registerMessage.setText("Signing you in...");
        accounts.logIn(outcome.account().username(), password).thenAccept(signIn ->
                SwingUtilities.invokeLater(() -> completeSignIn(signIn, registerMessage)));
    }

    private void completeSignIn(AccountClient.Outcome outcome, JLabel message) {
        if (!outcome.isLoggedIn()) {
            message.setText(outcome.message());
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
}
