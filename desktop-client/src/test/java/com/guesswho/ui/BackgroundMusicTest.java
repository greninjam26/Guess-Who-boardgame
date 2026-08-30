package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackgroundMusicTest {
    private Preferences preferences;

    @BeforeEach
    void freshPreferences() throws Exception {
        preferences = Preferences.userRoot().node("guesswho-test-" + System.nanoTime());
    }

    @Test
    void silenceIsTheQuietestTheControlAllows() {
        assertEquals(-80f, BackgroundMusic.gainFor(0, -80f, 6f));
    }

    @Test
    void fullVolumeIsUnchangedGain() {
        assertEquals(0f, BackgroundMusic.gainFor(100, -80f, 6f));
    }

    @Test
    void halfVolumeIsAboutSixDecibelsDown() {
        float gain = BackgroundMusic.gainFor(50, -80f, 6f);

        assertTrue(gain < -5.5f && gain > -6.5f,
                "Halving loudness is roughly -6dB, not half the decibel range: " + gain);
    }

    @Test
    void theMiddleOfTheSliderIsNotTheMiddleOfTheRange() {
        float half = BackgroundMusic.gainFor(50, -80f, 6f);
        float midpointOfRange = (-80f + 6f) / 2;

        assertTrue(half > midpointOfRange,
                "Wiring the slider straight to decibels is what makes it feel broken");
    }

    @Test
    void staysWithinWhatTheControlAccepts() {
        assertEquals(-10f, BackgroundMusic.gainFor(1, -10f, 6f),
                "A very low volume must not ask for a gain below the minimum");
    }

    @Test
    void everyControlWorksWithNothingToPlay() {
        BackgroundMusic music = new BackgroundMusic(Optional.empty(), preferences);

        music.start();
        music.volume(30);
        music.muted(true);
        music.pause();
        music.resume();
        music.close();

        assertEquals(30, music.volume());
        assertTrue(music.isMuted());
    }

    @Test
    void remembersVolumeBetweenSessions() {
        new BackgroundMusic(Optional.empty(), preferences).volume(25);

        assertEquals(25, new BackgroundMusic(Optional.empty(), preferences).volume());
    }

    @Test
    void remembersBeingMuted() {
        new BackgroundMusic(Optional.empty(), preferences).muted(true);

        assertTrue(new BackgroundMusic(Optional.empty(), preferences).isMuted());
    }

    @Test
    void mutingKeepsTheVolumeForWhenItComesBack() {
        BackgroundMusic music = new BackgroundMusic(Optional.empty(), preferences);
        music.volume(40);

        music.muted(true);

        assertEquals(40, music.volume(), "Unmuting should return to where it was");
    }

    @Test
    void keepsTheVolumeWithinRange() {
        BackgroundMusic music = new BackgroundMusic(Optional.empty(), preferences);

        music.volume(150);
        assertEquals(100, music.volume());

        music.volume(-20);
        assertEquals(0, music.volume());
    }

    @Test
    void tracksWhetherItIsPlaying() {
        BackgroundMusic music = new BackgroundMusic(Optional.empty(), preferences);

        music.start();
        assertTrue(music.isPlaying());

        music.pause();
        assertFalse(music.isPlaying());

        music.resume();
        assertTrue(music.isPlaying());
    }
}
