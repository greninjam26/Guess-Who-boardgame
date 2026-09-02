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
 * computer, beating a friend over the network, and beating somebody sitting
 * next to you are not comparable results — and the last of those is not
 * refereed by anything.
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
     * <p>Online and same-machine games are two boards rather than one "vs
     * Player", because they are not equally believable. An online game is
     * refereed by the server: it decides whose turn it is, it settles the
     * guess, and each player's own client answers for a character committed to
     * before play. A same-machine game is refereed by the one person holding
     * the keyboard for both sides, who can hand themselves a win as fast as
     * they can click. Putting those in one table would rank a result anybody
     * can manufacture alongside one they have to earn.</p>
     *
     * <p>Three tables at this player count is more empty space than anybody
     * wants, and it is still the right trade: a leaderboard that mixes them is
     * not a smaller problem than a leaderboard with a quiet tab.</p>
     *
     * @param leaderboardClient client used by each board
     * @return the tabbed boards
     */
    static JTabbedPane boards(LeaderboardClient leaderboardClient) {
        JTabbedPane boards = new JTabbedPane();
        boards.addTab("vs Computer", new LeaderboardPanel(leaderboardClient, GameMode.PVE));
        //Online first of the two: it is the competitive one.
        boards.addTab("vs Player (online)",
                new LeaderboardPanel(leaderboardClient, GameMode.PVP_ONLINE));
        boards.addTab("vs Player (same machine)",
                new LeaderboardPanel(leaderboardClient, GameMode.PVP_LOCAL));
        boards.setToolTipTextAt(1, "Games against a friend over the network, refereed by the server");
        boards.setToolTipTextAt(2,
                "Two people sharing one keyboard. Kept separate because one player controls both sides");
        return boards;
    }
}
