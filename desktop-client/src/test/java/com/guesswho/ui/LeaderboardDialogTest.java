package com.guesswho.ui;

import com.guesswho.game.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LeaderboardDialogTest {
    @Test
    void showsOneBoardPerGameModeWithoutCombiningThem() throws Exception {
        List<GameMode> requestedModes = Collections.synchronizedList(new ArrayList<>());

        JTabbedPane[] boards = new JTabbedPane[1];
        SwingUtilities.invokeAndWait(() -> boards[0] = LeaderboardDialog.boards(mode -> {
            requestedModes.add(mode);
            return CompletableFuture.completedFuture(List.of());
        }));
        SwingUtilities.invokeAndWait(() -> {
        });

        assertEquals(2, boards[0].getTabCount());
        assertEquals("vs Computer", boards[0].getTitleAt(0));
        assertEquals("vs Player", boards[0].getTitleAt(1));
        assertEquals(List.of(GameMode.PVE, GameMode.PVP_LOCAL), requestedModes);
    }
}
