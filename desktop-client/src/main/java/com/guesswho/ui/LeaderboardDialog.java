package com.guesswho.ui;

import com.guesswho.client.LeaderboardClient;
import com.guesswho.game.GameMode;

import java.awt.Dimension;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;

/**
 * Opens leaderboard standings in a separate non-modal window, with one board
 * per game mode. Standings are not combined across modes because beating the
 * computer and beating another player are not comparable results.
 */
final class LeaderboardDialog {
    private static final Dimension WINDOW_SIZE = new Dimension(520, 400);

    private LeaderboardDialog() {
    }

    static void show(JFrame owner, LeaderboardClient leaderboardClient) {
        JDialog dialog = new JDialog(owner, "Leaderboard", false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        dialog.setContentPane(boards(leaderboardClient));
        dialog.setPreferredSize(WINDOW_SIZE);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /**
     * Builds one board per game mode. Separate from {@link #show} so the tab
     * wiring can be exercised without a window.
     *
     * @param leaderboardClient client used by each board
     * @return the tabbed boards
     */
    static JTabbedPane boards(LeaderboardClient leaderboardClient) {
        JTabbedPane boards = new JTabbedPane();
        boards.addTab("vs Computer", new LeaderboardPanel(leaderboardClient, GameMode.PVE));
        boards.addTab("vs Player", new LeaderboardPanel(leaderboardClient, GameMode.PVP_LOCAL));
        return boards;
    }
}
