import java.io.InputStream;
import javax.swing.ImageIcon;

public class GameResourcesTest {
    public static void main(String[] args) throws Exception {
        opensPackagedQuestionData();
        opensPackagedCharacterData();
        boardLoadsPackagedData();
        loadsAndScalesCharacterImage();
        loadsAndScalesEliminatedCharacterImage();
        ignoresInvalidBackgroundMusic();
    }

    private static void opensPackagedQuestionData() throws Exception {
        try (InputStream input = GameResources.openQuestionData()) {
            check(input.read() != -1, "Question data should not be empty");
        }
    }

    private static void opensPackagedCharacterData() throws Exception {
        try (InputStream input = GameResources.openCharacterData()) {
            check(input.read() != -1, "Character data should not be empty");
        }
    }

    private static void boardLoadsPackagedData() throws Exception {
        Board board = new Board();

        check(board.getQuestionsList().size() == 19, "Board should load 19 questions");
        check(board.getCharacters().size() == 24, "Board should load 24 characters");
    }

    private static void loadsAndScalesCharacterImage() {
        ImageIcon icon = GameResources.loadCharacterIcon(0, 100, 150);

        check(icon.getIconWidth() == 100, "Character image should be 100 pixels wide");
        check(icon.getIconHeight() == 150, "Character image should be 150 pixels high");
    }

    private static void loadsAndScalesEliminatedCharacterImage() {
        ImageIcon icon = GameResources.loadEliminatedCharacterIcon(100, 150);

        check(icon.getIconWidth() == 100, "Eliminated image should be 100 pixels wide");
        check(icon.getIconHeight() == 150, "Eliminated image should be 150 pixels high");
    }

    private static void ignoresInvalidBackgroundMusic() {
        check(GameResources.loadBackgroundMusic().isEmpty(),
                "Invalid background music should be ignored");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
