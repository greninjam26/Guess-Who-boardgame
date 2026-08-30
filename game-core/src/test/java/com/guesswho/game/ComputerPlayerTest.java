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

        for (Character character : easyComputer.getPossibleCharacters()) {
            assertEquals("Brown", character.getEyeColour());
        }
        assertFalse(easyComputer.getPossibleCharacters().isEmpty(),
                "Brown-eyed characters should remain");
    }

    @Test
    void yesAnswerEliminatesCharactersWithoutTheAskedAttribute() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer("easy", "", alwaysChooseFirst());
        Question blueEyes = easyComputer.playQuestion();

        easyComputer.askQuestion(blueEyes.getQuestion(), "yes");

        for (Character character : easyComputer.getPossibleCharacters()) {
            assertEquals("Blue", character.getEyeColour());
        }
        assertFalse(easyComputer.getPossibleCharacters().isEmpty(), "Blue-eyed characters should remain");
    }

    @Test
    void noAnswerEliminatesCharactersWithTheAskedAttribute() throws Exception {
        ComputerPlayer easyComputer = new ComputerPlayer("easy", "", alwaysChooseFirst());
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
    void initializesQuestionsFromTheBoardCollection() throws Exception {
        Board reducedBoard = new Board();
        reducedBoard.getQuestionsList().remove(reducedBoard.getQuestionsList().size() - 1);

        ComputerPlayer easyComputer = new ComputerPlayer(
                "easy", "", reducedBoard, alwaysChooseFirst());

        assertEquals(18, easyComputer.getUnAskedQuestions().size());
    }

    @Test
    void filtersOnTheQuestionAskedRatherThanAPreviouslyChosenOne() throws Exception {
        Board board = new Board();
        ComputerPlayer hardComputer = new ComputerPlayer("hard", "", board, new Random(1));
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
        ComputerPlayer hardComputer = new ComputerPlayer("hard", "", board, new Random(42));
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
        ComputerPlayer hardComputer = new ComputerPlayer("hard", "", board, new Random(7));

        Question question = hardComputer.playQuestion();
        hardComputer.askQuestion(question.getQuestion(), "yes");

        assertArrayEquals(boardCountsBefore, board.getPeopleCount(),
                "Eliminating characters must not mutate the board's own counts");
    }

    @Test
    void twoPlayersSharingABoardRuleOutSeparately() throws Exception {
        Board shared = new Board();
        ComputerPlayer first = new ComputerPlayer("hard", "", shared, new Random(1));
        ComputerPlayer second = new ComputerPlayer("hard", "", shared, new Random(2));

        first.ruleOut(0);

        assertEquals(23, first.getPossibleCharacters().size());
        assertEquals(24, second.getPossibleCharacters().size(),
                "Eliminations used to live on the board's own characters, so both saw them");
    }

    @Test
    void startsWithEveryCharacterInTheRunning() throws Exception {
        assertEquals(24, new ComputerPlayer("hard", "").getPossibleCharacters().size());
    }

    @Test
    void rulingOutTheSameCharacterTwiceCountsOnce() throws Exception {
        ComputerPlayer computer = new ComputerPlayer("hard", "");

        computer.ruleOut(3);
        computer.ruleOut(3);

        assertEquals(23, computer.getPossibleCharacters().size());
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
