import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardTest {
    private Board board;

    @BeforeEach
    void createBoard() throws Exception {
        board = new Board();
    }

    @Test
    void loadsAllCharactersAndQuestions() {
        assertEquals(24, board.getCharacters().size());
        assertEquals(19, board.getQuestionsList().size());
    }

    @Test
    void findsQuestionByItsDisplayedText() {
        Question expected = board.getQuestionsList().get(3);
        Question actual = board.findQuestion(expected.getQuestion());

        assertEquals(expected.getQuestion(), actual.getQuestion());
        assertEquals(expected.getQuestionIndex(), actual.getQuestionIndex());
    }
}
