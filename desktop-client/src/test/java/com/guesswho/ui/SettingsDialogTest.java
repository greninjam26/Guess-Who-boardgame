package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsDialogTest {
    private BackgroundMusic music;
    private final List<String> actions = new ArrayList<>();

    @BeforeEach
    void freshMusic() {
        music = new BackgroundMusic(Optional.empty(),
                Preferences.userRoot().node("guesswho-test-" + System.nanoTime()));
    }

    @Test
    void offersTheActionsThatUsedToSitAcrossEveryScreen() throws Exception {
        JPanel panel = contents();

        assertTrue(button(panel, "Leaderboard") != null);
        assertTrue(button(panel, "Restart") != null);
        assertTrue(button(panel, "Quit") != null);
    }

    @Test
    void showsTheSavedVolume() throws Exception {
        music.volume(35);

        JSlider volume = find(contents(), JSlider.class);

        assertEquals(35, volume.getValue());
    }

    @Test
    void changingTheSliderChangesTheVolume() throws Exception {
        JPanel panel = contents();
        JSlider volume = find(panel, JSlider.class);

        SwingUtilities.invokeAndWait(() -> volume.setValue(15));

        assertEquals(15, music.volume());
    }

    @Test
    void mutingSilencesWithoutLosingTheVolume() throws Exception {
        music.volume(45);
        JPanel panel = contents();

        SwingUtilities.invokeAndWait(checkBox(panel, "Mute")::doClick);

        assertTrue(music.isMuted());
        assertEquals(45, music.volume());
    }

    @Test
    void showsMutedWhenItWasMutedLastTime() throws Exception {
        music.muted(true);

        assertTrue(checkBox(contents(), "Mute").isSelected());
    }

    @Test
    void stopsAndResumesTheMusic() throws Exception {
        music.start();
        JPanel panel = contents();

        SwingUtilities.invokeAndWait(checkBox(panel, "Play")::doClick);
        assertFalse(music.isPlaying());

        SwingUtilities.invokeAndWait(checkBox(panel, "Play")::doClick);
        assertTrue(music.isPlaying());
    }

    @Test
    void restartingTellsTheApplication() throws Exception {
        JPanel panel = contents();

        SwingUtilities.invokeAndWait(button(panel, "Restart")::doClick);

        assertEquals(List.of("restart"), actions);
    }

    @Test
    void quittingTellsTheApplication() throws Exception {
        JPanel panel = contents();

        SwingUtilities.invokeAndWait(button(panel, "Quit")::doClick);

        assertEquals(List.of("quit"), actions);
    }

    // --- helpers -------------------------------------------------------

    private JPanel contents() throws Exception {
        AtomicReference<JPanel> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> reference.set(SettingsDialog.contents(
                null,
                null,
                music,
                mode -> CompletableFuture.completedFuture(List.of()),
                () -> actions.add("restart"),
                () -> actions.add("quit"))));
        return reference.get();
    }

    private JButton button(Container container, String label) {
        for (JButton candidate : all(container, JButton.class)) {
            if (label.equals(candidate.getText())) {
                return candidate;
            }
        }
        throw new AssertionError("No button labelled " + label);
    }

    private JCheckBox checkBox(Container container, String label) {
        for (JCheckBox candidate : all(container, JCheckBox.class)) {
            if (label.equals(candidate.getText())) {
                return candidate;
            }
        }
        throw new AssertionError("No checkbox labelled " + label);
    }

    private <T extends Component> T find(Container container, Class<T> type) {
        return all(container, type).get(0);
    }

    /** Descends through panels only, so no control's internals are mistaken for one. */
    @SuppressWarnings("unchecked")
    private <T extends Component> List<T> all(Container container, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                found.add((T) child);
            }
            else if (child instanceof JPanel nested) {
                found.addAll(all(nested, type));
            }
        }
        return found;
    }
}
