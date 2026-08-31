package com.guesswho.ui;

import com.guesswho.game.GameResources;

import java.util.Optional;
import java.util.prefs.Preferences;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * The background music, and the three things a player wants to do to it: change
 * how loud it is, silence it, and stop it.
 *
 * <p>Every control works whether or not there is anything to play. A machine
 * with no sound device, or a build without the audio file, leaves the clip
 * absent; a settings screen that only worked when it happened to load would be
 * worse than one that quietly does nothing.</p>
 */
class BackgroundMusic {
    private static final String VOLUME_KEY = "musicVolume";
    private static final String MUTED_KEY = "musicMuted";
    private static final int DEFAULT_VOLUME = 60;

    private final Optional<Clip> clip;
    private final Preferences preferences;

    private int volume;
    private boolean muted;
    private boolean playing;

    /**
     * Loads the bundled music and the player's saved preferences.
     */
    BackgroundMusic() {
        this(GameResources.loadBackgroundMusic(),
                Preferences.userNodeForPackage(BackgroundMusic.class));
    }

    BackgroundMusic(Optional<Clip> clip, Preferences preferences) {
        this.clip = clip;
        this.preferences = preferences;
        volume = preferences.getInt(VOLUME_KEY, DEFAULT_VOLUME);
        muted = preferences.getBoolean(MUTED_KEY, false);
    }

    /**
     * Starts the music looping, at the saved volume.
     */
    void start() {
        playing = true;
        applyGain();
        clip.ifPresent(track -> {
            track.setFramePosition(0);
            track.loop(Clip.LOOP_CONTINUOUSLY);
        });
    }

    /**
     * Stops the music where it is, so it can resume from the same point.
     */
    void pause() {
        playing = false;
        clip.ifPresent(Clip::stop);
    }

    /**
     * Resumes from where it was paused.
     */
    void resume() {
        playing = true;
        clip.ifPresent(track -> track.loop(Clip.LOOP_CONTINUOUSLY));
    }

    /**
     * Reports whether the music is currently running.
     *
     * @return {@code true} while playing
     */
    boolean isPlaying() {
        return playing;
    }

    /**
     * Returns the saved volume.
     *
     * @return volume from 0 to 100
     */
    int volume() {
        return volume;
    }

    /**
     * Sets and remembers the volume.
     *
     * @param newVolume volume from 0 to 100, clamped to that range
     */
    void volume(int newVolume) {
        volume = Math.max(0, Math.min(100, newVolume));
        preferences.putInt(VOLUME_KEY, volume);
        applyGain();
    }

    /**
     * Reports whether the music is silenced.
     *
     * @return {@code true} when muted
     */
    boolean isMuted() {
        return muted;
    }

    /**
     * Silences the music without forgetting the volume.
     *
     * @param newMuted {@code true} to silence
     */
    void muted(boolean newMuted) {
        muted = newMuted;
        preferences.putBoolean(MUTED_KEY, muted);
        applyGain();
    }

    /**
     * Releases the audio device.
     */
    void close() {
        playing = false;
        clip.ifPresent(Clip::close);
    }

    private void applyGain() {
        clip.filter(track -> track.isControlSupported(FloatControl.Type.MASTER_GAIN))
                .ifPresent(track -> {
                    FloatControl gain =
                            (FloatControl) track.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(gainFor(
                            muted ? 0 : volume, gain.getMinimum(), gain.getMaximum()));
                });
    }

    /**
     * Converts a volume from 0 to 100 into a gain in decibels.
     *
     * <p>{@code MASTER_GAIN} is measured in decibels, which is logarithmic. A
     * slider wired straight to it does almost nothing across its upper half and
     * then collapses to silence near the bottom, because halving the decibels is
     * nothing like halving the loudness. Converting through {@code 20·log10}
     * makes the middle of the slider sound like the middle.</p>
     *
     * @param volume volume from 0 to 100
     * @param minimum quietest gain the control accepts
     * @param maximum loudest gain the control accepts
     * @return the gain to set, with zero volume mapping to the minimum
     */
    static float gainFor(int volume, float minimum, float maximum) {
        if (volume <= 0) {
            return minimum;
        }
        float decibels = (float) (20.0 * Math.log10(Math.min(100, volume) / 100.0));
        return Math.max(minimum, Math.min(maximum, decibels));
    }
}
