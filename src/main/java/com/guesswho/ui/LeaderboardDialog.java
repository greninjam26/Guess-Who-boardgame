package com.guesswho.ui;

import com.guesswho.client.LeaderboardClient;

import java.awt.Dimension;
import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 * Opens leaderboard standings in a separate non-modal window.
 */
final class LeaderboardDialog {
    private static final Dimension WINDOW_SIZE = new Dimension(520, 360);

    private LeaderboardDialog() {
    }

    static void show(JFrame owner, LeaderboardClient leaderboardClient) {
        JDialog dialog = new JDialog(owner, "Leaderboard", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(new LeaderboardPanel(leaderboardClient));
        dialog.setPreferredSize(WINDOW_SIZE);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
