import java.awt.Image;
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

    private static ImageIcon loadScaledIcon(String path, int width, int height) {
        ImageIcon icon = new ImageIcon(requiredResource(path));
        Image scaledImage = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
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
