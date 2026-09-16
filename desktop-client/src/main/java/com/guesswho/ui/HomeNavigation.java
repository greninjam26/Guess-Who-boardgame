package com.guesswho.ui;

import java.awt.Container;
import java.util.function.BooleanSupplier;

/** Replaces the current game with home without replacing its application window. */
final class HomeNavigation {
    private HomeNavigation() {
    }

    static void returnHome(
            boolean gameActive,
            BooleanSupplier confirmation,
            Runnable stopGame,
            Container contents,
            Runnable buildHome) {
        if (gameActive && !confirmation.getAsBoolean()) {
            return;
        }
        stopGame.run();
        contents.removeAll();
        buildHome.run();
        contents.revalidate();
        contents.repaint();
    }
}
