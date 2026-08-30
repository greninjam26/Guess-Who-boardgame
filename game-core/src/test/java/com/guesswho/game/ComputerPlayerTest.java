package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        computerPlayer = new ComputerPlayer(ComputerDifficulty.HARD, "");
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
        ComputerPlayer easyComputer = new ComputerPlayer(ComputerDifficulty.EASY, "", alwaysChooseFirst());
        Question firstQuestion = easyComputer.playQuestion();
        easyComputer.askQuestion(firstQuestion.getQuestion(), "no");

        Question secondQuestion = easyComputer.playQuestion();

        assertNotEquals(firstQuestion.getQuestion(), secondQuestion.getQuestion());
    }

    @Test
    void easyModeFiltersUsingTheSelectedQuestionIndex() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer(ComputerDifficulty.EASY, "", alwaysChooseFirst());
        Question blueEyes = easyComputer.playQuestion();
        easyComputer.askQuestion(blueEyes.getQuestion(), "no");
        Question brownEyes = easyComputer.playQuestion();

        easyComputer.askQuestion(brownEyes.getQuestion(), "yes");

        for (Character character : easyComputer.getPossibleCharacters()) {
            assertEquals("Brown", character.getEyeColour());
        }
        assertFalse(easyComputer.getPossibleCharacters().isEmpty(),
                "Brown-eyed characters should remain");
    }

    @Test
    void yesAnswerEliminatesCharactersWithoutTheAskedAttribute() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer(ComputerDifficulty.EASY, "", alwaysChooseFirst());
        Question blueEyes = easyComputer.playQuestion();

        easyComputer.askQuestion(blueEyes.getQuestion(), "yes");

        for (Character character : easyComputer.getPossibleCharacters()) {
            assertEquals("Blue", character.getEyeColour());
        }
        assertFalse(easyComputer.getPossibleCharacters().isEmpty(), "Blue-eyed characters should remain");
    }

    @Test
    void noAnswerEliminatesCharactersWithTheAskedAttribute() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer(ComputerDifficulty.EASY, "", alwaysChooseFirst());
        Question blueEyes = easyComputer.playQuestion();

        easyComputer.askQuestion(blueEyes.getQuestion(), "no");

        for (Character character : easyComputer.getPossibleCharacters()) {
            assertNotEquals("Blue", character.getEyeColour());
        }
        assertFalse(easyComputer.getPossibleCharacters().isEmpty(), "Non-blue-eyed characters should remain");
    }

    @Test
    void matchingAnswersConvergeToTheSelectedCharacter() throws Exception {
        Board board = new Board();
        ComputerPlayer easyComputer = new ComputerPlayer(
                ComputerDifficulty.EASY, "", board, alwaysChooseFirst());
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
    void initializesQuestionsFromTheBoardCollection() throws Exception {
        Board reducedBoard = new Board();
        reducedBoard.getQuestionsList().remove(reducedBoard.getQuestionsList().size() - 1);

        ComputerPlayer easyComputer = new ComputerPlayer(
                ComputerDifficulty.EASY, "", reducedBoard, alwaysChooseFirst());

        assertEquals(18, easyComputer.getUnAskedQuestions().size());
    }

    @Test
    void filtersOnTheQuestionAskedRatherThanAPreviouslyChosenOne() throws Exception {
        Board board = new Board();
        ComputerPlayer hardComputer = new ComputerPlayer(ComputerDifficulty.HARD, "", board, new Random(1));
        Question glasses = board.findQuestion("Does your character wear glasses?");

        hardComputer.askQuestion(glasses.getQuestion(), "yes");

        for (Character character : hardComputer.getPossibleCharacters()) {
            assertTrue(character.getIsGlasses(),
                    character.getName() + " survived a \"yes\" to the glasses question"
                            + " without wearing glasses");
        }
        assertEquals(board.getPeopleCount()[glasses.getQuestionIndex()],
                hardComputer.getPossibleCharacters().size(),
                "Everyone who wears glasses should still be in the running");
    }

    @Test
    void hardModeNeverEliminatesTheCharacterItIsTruthfullyToldAbout() throws Exception {
        Board board = new Board();
        ComputerPlayer hardComputer = new ComputerPlayer(ComputerDifficulty.HARD, "", board, new Random(42));
        Character target = hardComputer.findCharacter("Sam");

        while (!hardComputer.getUnAskedQuestions().isEmpty() && !hardComputer.onlyOne()) {
            Question question = hardComputer.playQuestion();
            boolean matchesTarget = board.getAnswers()
                    [target.getCharacterIndex()][question.getQuestionIndex()];

            hardComputer.askQuestion(question.getQuestion(), matchesTarget ? "yes" : "no");

            assertTrue(hardComputer.getPossibleCharacters().contains(target),
                    "Filtering must use the question that was asked, not a stale index");
        }

        assertTrue(hardComputer.onlyOne());
        assertEquals("Sam", hardComputer.lastOne());
    }

    @Test
    void ownsItsEliminationStateInsteadOfMutatingTheBoard() throws Exception {
        Board board = new Board();
        int[] boardCountsBefore = board.getPeopleCount().clone();
        ComputerPlayer hardComputer = new ComputerPlayer(ComputerDifficulty.HARD, "", board, new Random(7));

        Question question = hardComputer.playQuestion();
        hardComputer.askQuestion(question.getQuestion(), "yes");

        assertArrayEquals(boardCountsBefore, board.getPeopleCount(),
                "Eliminating characters must not mutate the board's own counts");
    }

    @Test
    void twoPlayersSharingABoardRuleOutSeparately() throws Exception {
        Board shared = new Board();
        ComputerPlayer first = new ComputerPlayer(ComputerDifficulty.HARD, "", shared, new Random(1));
        ComputerPlayer second = new ComputerPlayer(ComputerDifficulty.HARD, "", shared, new Random(2));

        first.ruleOut(0);

        assertEquals(23, first.getPossibleCharacters().size());
        assertEquals(24, second.getPossibleCharacters().size(),
                "Eliminations used to live on the board's own characters, so both saw them");
    }

    @Test
    void startsWithEveryCharacterInTheRunning() throws Exception {
        assertEquals(24, new ComputerPlayer(ComputerDifficulty.HARD, "").getPossibleCharacters().size());
    }

    @Test
    void rulingOutTheSameCharacterTwiceCountsOnce() throws Exception {
        ComputerPlayer computer = new ComputerPlayer(ComputerDifficulty.HARD, "");

        computer.ruleOut(3);
        computer.ruleOut(3);

        assertEquals(23, computer.getPossibleCharacters().size());
    }

    @Test
    void theEasyLevelWaitsForCertaintyBeforeGuessing() throws Exception {
        ComputerPlayer easy = new ComputerPlayer(ComputerDifficulty.EASY, "");
        leaveOnly(easy, 2);

        assertFalse(easy.readyToGuess(),
                "Two candidates is a coin flip, and the easy level does not gamble");

        leaveOnly(easy, 1);
        assertTrue(easy.readyToGuess());
    }

    @Test
    void theHardLevelGamblesOnACoinFlipRatherThanSpendATurn() throws Exception {
        ComputerPlayer hard = new ComputerPlayer(ComputerDifficulty.HARD, "");
        leaveOnly(hard, 2);

        assertTrue(hard.readyToGuess(),
                "Waiting another turn can lose a game that guessing would have won");
    }

    @Test
    void neitherLevelGuessesWithTheFieldStillOpen() throws Exception {
        assertFalse(new ComputerPlayer(ComputerDifficulty.EASY, "").readyToGuess());
        assertFalse(new ComputerPlayer(ComputerDifficulty.HARD, "").readyToGuess());
    }

    @Test
    void namesOneOfTheCharactersItHasNotRuledOut() throws Exception {
        ComputerPlayer hard = new ComputerPlayer(ComputerDifficulty.HARD, "");
        leaveOnly(hard, 2);

        assertTrue(hard.getPossibleCharacters().stream()
                        .anyMatch(character -> character.getName().equals(hard.bestGuess())),
                "A guess must be someone still in the running");
    }

    /** Rules out everyone beyond the first {@code remaining} characters. */
    private void leaveOnly(ComputerPlayer computer, int remaining) {
        for (int index = remaining; index < 24; index++) {
            computer.ruleOut(index);
        }
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
