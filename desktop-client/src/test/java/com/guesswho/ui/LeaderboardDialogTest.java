package com.guesswho.ui;

import com.guesswho.game.GameMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(3, boards[0].getTabCount());
        assertEquals("vs Computer", boards[0].getTitleAt(0));
        assertEquals("vs Player (online)", boards[0].getTitleAt(1));
        assertEquals("vs Player (same machine)", boards[0].getTitleAt(2));
        assertEquals(List.of(GameMode.PVE, GameMode.PVP_ONLINE, GameMode.PVP_LOCAL),
                requestedModes);
    }

    @Test
    void asksForOnlineGamesSoTheyAreNotRecordedAndNeverShown() throws Exception {
        //The gap this closes: online results were being written correctly and
        //nothing ever asked for them, because the only player board requested
        //PVP_LOCAL. A game you played was on nobody's leaderboard.
        List<GameMode> requestedModes = Collections.synchronizedList(new ArrayList<>());

        SwingUtilities.invokeAndWait(() -> LeaderboardDialog.boards(mode -> {
            requestedModes.add(mode);
            return CompletableFuture.completedFuture(List.of());
        }));
        SwingUtilities.invokeAndWait(() -> {
        });

        assertTrue(requestedModes.contains(GameMode.PVP_ONLINE),
                "No board asks for online games, so they are recorded and never seen");
    }

    @Test
    void keepsTheSelfRefereedGamesOffTheOnlineBoard() throws Exception {
        //Two boards rather than one, because one player holding the keyboard for
        //both sides can hand themselves a win. Merging them would rank that
        //alongside a game the server refereed.
        List<GameMode> requestedModes = Collections.synchronizedList(new ArrayList<>());

        SwingUtilities.invokeAndWait(() -> LeaderboardDialog.boards(mode -> {
            requestedModes.add(mode);
            return CompletableFuture.completedFuture(List.of());
        }));
        SwingUtilities.invokeAndWait(() -> {
        });

        assertEquals(1, Collections.frequency(requestedModes, GameMode.PVP_ONLINE));
        assertEquals(1, Collections.frequency(requestedModes, GameMode.PVP_LOCAL));
        assertFalse(requestedModes.contains(null),
                "A board asking for every mode at once would combine them again");
    }
}
