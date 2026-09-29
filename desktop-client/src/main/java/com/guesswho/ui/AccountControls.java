package com.guesswho.ui;

import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Keeps the current account state and its setup-only action visible. */
class AccountControls extends JPanel {
    private final Runnable signIn;
    private final Runnable signOut;
    private final JLabel status = new JLabel();
    private final JButton action = new JButton();
    private boolean signedIn;

    AccountControls(Runnable signIn, Runnable signOut) {
        this.signIn = signIn;
        this.signOut = signOut;
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        setBackground(UiTheme.SURFACE);
        status.setForeground(UiTheme.TEXT);
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        UiTheme.styleToolbarButton(action);
        action.addActionListener(event -> {
            if (signedIn) {
                signOut.run();
            }
            else {
                signIn.run();
            }
        });
        add(status);
        add(action);
        show(Optional.empty());
    }

    /** Updates the visible identity after login, logout, or session restore. */
    void show(Optional<String> username) {
        signedIn = username.isPresent();
        status.setText(username.map(name -> "Signed in as " + name)
                .orElse("Playing as guest"));
        action.setText(signedIn ? "Sign out" : "Sign in / Create account");
    }

    /** Account changes are safe during setup, but not while a game is active. */
    void switchingAllowed(boolean allowed) {
        action.setEnabled(allowed);
        action.setToolTipText(allowed ? null : "Finish or leave the current game first");
    }
}
