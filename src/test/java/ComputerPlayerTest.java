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

    private Random alwaysChooseFirst() {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
