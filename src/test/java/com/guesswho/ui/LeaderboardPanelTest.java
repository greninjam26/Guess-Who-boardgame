package com.guesswho.ui;

import com.guesswho.leaderboard.LeaderboardEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LeaderboardPanelTest {
    @Test
    void showsLoadingThenDisplaysRetrievedStandings() throws Exception {
        CompletableFuture<List<LeaderboardEntry>> response = new CompletableFuture<>();
        AtomicReference<LeaderboardPanel> panelReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(
                new LeaderboardPanel(() -> response)));

        LeaderboardPanel panel = panelReference.get();
        JLabel statusLabel = findComponent(panel, JLabel.class);
        JTable table = findComponent(panel, JTable.class);
        assertEquals("Loading leaderboard...", statusLabel.getText());
        assertFalse(table.isVisible());

        response.complete(List.of(
                new LeaderboardEntry("Alex", 3, 2),
                new LeaderboardEntry("AI", 3, 1)));
        SwingUtilities.invokeAndWait(() -> {
        });

        assertFalse(statusLabel.isVisible());
        assertTrue(table.isVisible());
        assertEquals(2, table.getRowCount());
        assertEquals(1, table.getValueAt(0, 0));
        assertEquals("Alex", table.getValueAt(0, 1));
        assertEquals(3, table.getValueAt(0, 2));
        assertEquals(2, table.getValueAt(0, 3));
        assertEquals(2, table.getValueAt(1, 0));
        assertEquals("AI", table.getValueAt(1, 1));
    }

    @Test
    void showsEmptyStateWhenNoGamesHaveBeenRecorded() throws Exception {
        AtomicReference<LeaderboardPanel> panelReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new LeaderboardPanel(
                () -> CompletableFuture.completedFuture(List.of()))));
        SwingUtilities.invokeAndWait(() -> {
        });

        JLabel statusLabel = findComponent(panelReference.get(), JLabel.class);
        JTable table = findComponent(panelReference.get(), JTable.class);
        assertTrue(statusLabel.isVisible());
        assertEquals(
                "No completed games have been recorded yet.",
                statusLabel.getText());
        assertFalse(table.isVisible());
    }

    @Test
    void showsUnavailableStateWhenLeaderboardRequestFails() throws Exception {
        AtomicReference<LeaderboardPanel> panelReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new LeaderboardPanel(
                () -> CompletableFuture.failedFuture(
                        new IllegalStateException("Server unavailable")))));
        SwingUtilities.invokeAndWait(() -> {
        });

        JLabel statusLabel = findComponent(panelReference.get(), JLabel.class);
        JTable table = findComponent(panelReference.get(), JTable.class);
        assertTrue(statusLabel.isVisible());
        assertEquals(
                "Leaderboard is unavailable. Start the server and try again.",
                statusLabel.getText());
        assertFalse(table.isVisible());
    }

    @Test
    void retriesLeaderboardRequestAfterFailure() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<LeaderboardPanel> panelReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelReference.set(new LeaderboardPanel(() -> {
            if (requests.getAndIncrement() == 0) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Server unavailable"));
            }
            return CompletableFuture.completedFuture(
                    List.of(new LeaderboardEntry("Alex", 3, 2)));
        })));
        SwingUtilities.invokeAndWait(() -> {
        });

        JButton refreshButton = findButton(panelReference.get(), "Refresh");
        SwingUtilities.invokeAndWait(refreshButton::doClick);
        SwingUtilities.invokeAndWait(() -> {
        });

        JLabel statusLabel = findComponent(panelReference.get(), JLabel.class);
        JTable table = findComponent(panelReference.get(), JTable.class);
        assertEquals(2, requests.get());
        assertFalse(statusLabel.isVisible());
        assertTrue(table.isVisible());
        assertEquals("Alex", table.getValueAt(0, 1));
    }

    private <T extends Component> T findComponent(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container childContainer) {
                try {
                    return findComponent(childContainer, type);
                } catch (AssertionError ignored) {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("No component found for " + type.getSimpleName());
    }

    private JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container childContainer) {
                try {
                    return findButton(childContainer, text);
                } catch (AssertionError ignored) {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("No button found with text " + text);
    }
}
