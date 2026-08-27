import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComputerPlayerTest {
    private ComputerPlayer computerPlayer;

    @BeforeEach
    void createComputerPlayer() throws Exception {
        computerPlayer = new ComputerPlayer("hard", "");
    }

    @Test
    void answersQuestionsFromSelectedCharacterAttributes() {
        computerPlayer.setSelectedCharacter(computerPlayer.findCharacter("Sam"));

        assertTrue(computerPlayer.answerQuestion("Is your character's eye colour green?"));
        assertFalse(computerPlayer.answerQuestion("Is your character's eye colour blue?"));
    }

    @Test
    void choosesAQuestionDuringItsTurn() {
        assertNotNull(computerPlayer.playQuestion());
    }

    @Test
    void startsWithMoreThanOnePossibleCharacter() {
        assertFalse(computerPlayer.onlyOne());
    }

    @Test
    void easyModeDoesNotRepeatAnAnsweredQuestion() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer("easy", "", alwaysChooseFirst());
        Question firstQuestion = easyComputer.playQuestion();
        easyComputer.askQuestion(firstQuestion.getQuestion(), "no");

        Question secondQuestion = easyComputer.playQuestion();

        assertNotEquals(firstQuestion.getQuestion(), secondQuestion.getQuestion());
    }

    @Test
    void easyModeFiltersUsingTheSelectedQuestionIndex() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer("easy", "", alwaysChooseFirst());
        Question blueEyes = easyComputer.playQuestion();
        easyComputer.askQuestion(blueEyes.getQuestion(), "no");
        Question brownEyes = easyComputer.playQuestion();

        easyComputer.askQuestion(brownEyes.getQuestion(), "yes");

        int activeCharacters = 0;
        for (Character character : easyComputer.getPossibleCharacters()) {
            if (character.getIsActive()) {
                activeCharacters++;
                assertEquals("Brown", character.getEyeColour());
            }
        }
        assertTrue(activeCharacters > 0, "Brown-eyed characters should remain active");
    }

    @Test
    void yesAnswerEliminatesCharactersWithoutTheAskedAttribute() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer("easy", "", alwaysChooseFirst());
        Question blueEyes = easyComputer.playQuestion();

        easyComputer.askQuestion(blueEyes.getQuestion(), "yes");

        int activeCharacters = 0;
        for (Character character : easyComputer.getPossibleCharacters()) {
            if (character.getIsActive()) {
                activeCharacters++;
                assertEquals("Blue", character.getEyeColour());
            }
        }
        assertTrue(activeCharacters > 0, "Blue-eyed characters should remain active");
    }

    @Test
    void noAnswerEliminatesCharactersWithTheAskedAttribute() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer("easy", "", alwaysChooseFirst());
        Question blueEyes = easyComputer.playQuestion();

        easyComputer.askQuestion(blueEyes.getQuestion(), "no");

        int activeCharacters = 0;
        for (Character character : easyComputer.getPossibleCharacters()) {
            if (character.getIsActive()) {
                activeCharacters++;
                assertNotEquals("Blue", character.getEyeColour());
            }
        }
        assertTrue(activeCharacters > 0, "Non-blue-eyed characters should remain active");
    }

    @Test
    void matchingAnswersConvergeToTheSelectedCharacter() throws Exception {
        Board board = new Board();
        ComputerPlayer easyComputer = new ComputerPlayer(
                "easy", "", board, alwaysChooseFirst());
        Character target = easyComputer.findCharacter("Sam");

        while (!easyComputer.getUnAskedQuestions().isEmpty()) {
            Question question = easyComputer.playQuestion();
            boolean matchesTarget = board.getAnswers()
                    [target.getCharacterIndex()][question.getQuestionIndex()];
            easyComputer.askQuestion(question.getQuestion(), matchesTarget ? "yes" : "no");
        }

        assertTrue(easyComputer.onlyOne());
        assertEquals("Sam", easyComputer.lastOne());
    }

    @Test
    void reportsAWinWhenTheComputerGuessesIncorrectly() {
        assertTrue(computerPlayer.playGuess("Olivia", true).contains("you won"));
    }

    @Test
    void reportsALossWhenTheComputerGuessesCorrectly() {
        assertTrue(computerPlayer.playGuess("Olivia", false).contains("you lost"));
    }

    @Test
    void initializesQuestionsFromTheBoardCollection() throws Exception {
        Board reducedBoard = new Board();
        reducedBoard.getQuestionsList().remove(reducedBoard.getQuestionsList().size() - 1);

        ComputerPlayer easyComputer = new ComputerPlayer(
                "easy", "", reducedBoard, alwaysChooseFirst());

        assertEquals(18, easyComputer.getUnAskedQuestions().size());
    }

    private Random alwaysChooseFirst() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
