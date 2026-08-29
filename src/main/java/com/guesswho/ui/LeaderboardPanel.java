package com.guesswho.ui;

import com.guesswho.client.LeaderboardClient;
import com.guesswho.leaderboard.LeaderboardEntry;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/**
 * Displays leaderboard loading state and standings.
 */
class LeaderboardPanel extends JPanel {
    private final LeaderboardClient leaderboardClient;
    private final JLabel statusLabel = new JLabel(
            "Loading leaderboard...", SwingConstants.CENTER);
    private final JTable standingsTable = new JTable();
    private final JButton refreshButton = new JButton("Refresh");

    LeaderboardPanel(LeaderboardClient leaderboardClient) {
        super(new BorderLayout(8, 8));
        this.leaderboardClient = leaderboardClient;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        standingsTable.setFillsViewportHeight(true);
        standingsTable.setRowSelectionAllowed(false);
        standingsTable.getTableHeader().setReorderingAllowed(false);
        standingsTable.setVisible(false);
        add(statusLabel, BorderLayout.NORTH);
        add(new JScrollPane(standingsTable), BorderLayout.CENTER);
        add(refreshButton, BorderLayout.SOUTH);
        refreshButton.addActionListener(event -> loadStandings());
        loadStandings();
    }

    private void loadStandings() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Loading leaderboard...");
        statusLabel.setVisible(true);
        standingsTable.setVisible(false);
        leaderboardClient.fetch(null).whenComplete((standings, failure) ->
                SwingUtilities.invokeLater(() -> {
                    refreshButton.setEnabled(true);
                    if (failure != null) {
                        statusLabel.setText(
                                "Leaderboard is unavailable. Start the server and try again.");
                        statusLabel.setVisible(true);
                        standingsTable.setVisible(false);
                        return;
                    }
                    if (standings.isEmpty()) {
                        statusLabel.setText(
                                "No completed games have been recorded yet.");
                        statusLabel.setVisible(true);
                        standingsTable.setVisible(false);
                        return;
                    }
                    standingsTable.setModel(new LeaderboardTableModel(standings));
                    statusLabel.setVisible(false);
                    standingsTable.setVisible(true);
                }));
    }

    private static final class LeaderboardTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {
            "Rank", "Player", "Games Played", "Wins"
        };

        private final List<LeaderboardEntry> standings;

        private LeaderboardTableModel(List<LeaderboardEntry> standings) {
            this.standings = List.copyOf(standings);
        }

        @Override
        public int getRowCount() {
            return standings.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LeaderboardEntry entry = standings.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex + 1;
                case 1 -> entry.name();
                case 2 -> entry.gamesPlayed();
                case 3 -> entry.wins();
                default -> throw new IndexOutOfBoundsException(
                        "Unknown leaderboard column " + columnIndex);
            };
        }
    }
}
