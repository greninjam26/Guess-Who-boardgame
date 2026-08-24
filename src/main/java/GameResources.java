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

public final class GameResources {
    private GameResources() {
    }

    public static InputStream openQuestionData() throws IOException {
        return requiredResource("/data/QuestionDB.csv").openStream();
    }

    public static InputStream openCharacterData() throws IOException {
        return requiredResource("/data/GuessWhoDB.csv").openStream();
    }

    public static ImageIcon loadCharacterIcon(int characterIndex, int width, int height) {
        return loadScaledIcon("/images/" + characterIndex + ".jpg", width, height);
    }

    public static ImageIcon loadEliminatedCharacterIcon(int width, int height) {
        return loadScaledIcon("/images/characterGone.jpg", width, height);
    }

    private static ImageIcon loadScaledIcon(String path, int width, int height) {
        ImageIcon icon = new ImageIcon(requiredResource(path));
        Image scaledImage = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaledImage);
    }

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
