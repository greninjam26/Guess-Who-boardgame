package com.guesswho.game;

import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.ImageIcon;

/**
 * Loads board data and media bundled on the application classpath.
 */
public final class GameResources {
    private GameResources() {
    }

    /**
     * Opens the bundled question database.
     *
     * @return an input stream for the question CSV data
     * @throws IOException if the resource stream cannot be opened
     * @throws IllegalStateException if the resource is missing
     */
    public static InputStream openQuestionData() throws IOException {
        return requiredResource("/data/QuestionDB.csv").openStream();
    }

    /**
     * Opens the bundled character database.
     *
     * @return an input stream for the character CSV data
     * @throws IOException if the resource stream cannot be opened
     * @throws IllegalStateException if the resource is missing
     */
    public static InputStream openCharacterData() throws IOException {
        return requiredResource("/data/GuessWhoDB.csv").openStream();
    }

    /**
     * Loads and scales a character portrait.
     *
     * @param characterIndex character image index
     * @param width requested width in pixels
     * @param height requested height in pixels
     * @return the scaled character icon
     * @throws IllegalStateException if the image resource is missing
     */
    public static ImageIcon loadCharacterIcon(int characterIndex, int width, int height) {
        return loadScaledIcon("/images/" + characterIndex + ".jpg", width, height);
    }

    /**
     * Loads and scales the image shown for an eliminated character.
     *
     * @param width requested width in pixels
     * @param height requested height in pixels
     * @return the scaled eliminated-character icon
     * @throws IllegalStateException if the image resource is missing
     */
    public static ImageIcon loadEliminatedCharacterIcon(int width, int height) {
        return loadScaledIcon("/images/characterGone.jpg", width, height);
    }

    /**
     * Scales a portrait for display, keeping a second copy at twice the size.
     *
     * <p>A single bitmap scaled to the requested size looks soft on a high-DPI
     * screen, where the toolkit draws those logical points across twice as many
     * physical pixels and has nothing left to draw with. The source images are
     * far larger than either copy, so both are downscales and the detail is
     * there to keep.</p>
     */
    private static ImageIcon loadScaledIcon(String path, int width, int height) {
        Image source = new ImageIcon(requiredResource(path)).getImage();
        return new ImageIcon(new BaseMultiResolutionImage(
                scaled(source, width, height),
                scaled(source, width * 2, height * 2)));
    }

    /** Wrapped in an ImageIcon, which waits for the scaling to finish. */
    private static Image scaled(Image source, int width, int height) {
        return new ImageIcon(
                source.getScaledInstance(width, height, Image.SCALE_SMOOTH)).getImage();
    }

    /**
     * Attempts to load the bundled background music.
     *
     * @return an opened audio clip, or an empty value when the audio is missing,
     *         unsupported, or unavailable
     */
    public static Optional<Clip> loadBackgroundMusic() {
        URL resource = GameResources.class.getResource("/audio/Bloom of Youth.wav");
        if (resource == null) {
            return Optional.empty();
        }

        Clip clip = null;
        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(resource)) {
            clip = AudioSystem.getClip();
            clip.open(audioStream);
            return Optional.of(clip);
        }
        catch (IOException | LineUnavailableException | UnsupportedAudioFileException exception) {
            if (clip != null) {
                clip.close();
            }
            return Optional.empty();
        }
    }

    private static URL requiredResource(String path) {
        URL resource = GameResources.class.getResource(path);
        if (resource == null) {
            throw new IllegalStateException("Missing required resource: " + path);
        }
        return resource;
    }
}
