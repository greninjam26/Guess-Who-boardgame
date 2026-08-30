package com.guesswho.ui;

import com.guesswho.client.LeaderboardClient;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * Everything that is about the application rather than the game in progress.
 *
 * <p>Quitting, restarting, the leaderboard, and the music used to sit in a strip
 * across the top of every screen, including the ones where restarting means
 * nothing. They are actions a player reaches for occasionally, so they live
 * behind one button instead.</p>
 */
final class SettingsDialog {
    private SettingsDialog() {
    }

    /**
     * Opens the settings window.
     *
     * @param owner window the dialog belongs to
     * @param music the background music being controlled
     * @param leaderboardClient client used by the leaderboard window
     * @param onRestart starts a new game
     * @param onQuit closes the application
     */
    static void show(
            JFrame owner,
            BackgroundMusic music,
            LeaderboardClient leaderboardClient,
            Runnable onRestart,
            Runnable onQuit) {
        JDialog dialog = new JDialog(owner, "Settings", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(
                contents(owner, dialog, music, leaderboardClient, onRestart, onQuit));
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    /**
     * Builds the settings contents. Separate from {@link #show} so it can be
     * exercised without a window.
     *
     * @param owner window the leaderboard should belong to, or {@code null}
     * @param dialog window to close when an action is taken, or {@code null}
     * @param music the background music being controlled
     * @param leaderboardClient client used by the leaderboard window
     * @param onRestart starts a new game
     * @param onQuit closes the application
     * @return the settings panel
     */
    static JPanel contents(
            JFrame owner,
            JDialog dialog,
            BackgroundMusic music,
            LeaderboardClient leaderboardClient,
            Runnable onRestart,
            Runnable onQuit) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(musicControls(music), BorderLayout.CENTER);
        panel.add(actions(owner, dialog, leaderboardClient, onRestart, onQuit),
                BorderLayout.SOUTH);
        return panel;
    }

    private static JPanel musicControls(BackgroundMusic music) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Music"));

        JSlider volume = new JSlider(0, 100, music.volume());
        volume.setMajorTickSpacing(25);
        volume.setPaintTicks(true);
        volume.setPaintLabels(true);
        volume.addChangeListener(event -> music.volume(volume.getValue()));

        JCheckBox muted = new JCheckBox("Mute", music.isMuted());
        muted.addActionListener(event -> music.muted(muted.isSelected()));

        JCheckBox playing = new JCheckBox("Play", music.isPlaying());
        playing.addActionListener(event -> {
            if (playing.isSelected()) {
                music.resume();
                return;
            }
            music.pause();
        });

        panel.add(new JLabel("Volume"));
        panel.add(volume);
        panel.add(muted);
        panel.add(playing);
        return panel;
    }

    private static JPanel actions(
            JFrame owner,
            JDialog dialog,
            LeaderboardClient leaderboardClient,
            Runnable onRestart,
            Runnable onQuit) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        JButton leaderboard = new JButton("Leaderboard");
        JButton restart = new JButton("Restart");
        JButton quit = new JButton("Quit");
        leaderboard.addActionListener(event -> {
            close(dialog);
            LeaderboardDialog.show(owner, leaderboardClient);
        });
        restart.addActionListener(event -> {
            close(dialog);
            onRestart.run();
        });
        quit.addActionListener(event -> onQuit.run());
        panel.add(leaderboard);
        panel.add(restart);
        panel.add(quit);
        return panel;
    }

    private static void close(JDialog dialog) {
        if (dialog != null) {
            dialog.dispose();
        }
    }
}
