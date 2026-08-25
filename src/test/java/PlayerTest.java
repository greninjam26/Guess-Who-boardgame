import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class PlayerTest {
    @Test
    void rejectsUnknownCharacterNames() throws Exception {
        Player player = new Player("");

        assertThrows(IllegalArgumentException.class,
                () -> player.findCharacter("Unknown character"));
    }

    @Test
    void initializesFromTheBoardCollections() throws Exception {
        Board reducedBoard = new Board();
        reducedBoard.getCharacters().remove(reducedBoard.getCharacters().size() - 1);
        reducedBoard.getQuestionsList().remove(reducedBoard.getQuestionsList().size() - 1);

        Player player = new Player("", reducedBoard, alwaysChooseLast());

        assertTrue(reducedBoard.getCharacters().contains(player.getSelectedCharacter()));
        assertEquals(18, player.getUnAskedQuestions().size());
    }

    private Random alwaysChooseLast() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };
    }
}
