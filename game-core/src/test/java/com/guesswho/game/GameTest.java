package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.game.QuestionMode;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameTest {
    private Game game;

    @BeforeEach
    void createGame() {
        game = new Game();
    }

    @Test
    void newGameStartsWithoutAWinner() {
        assertEquals(GameStatus.STARTING, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void playerCanStartBeforeComputer() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getComputerPlayer().getIsTurn());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }

    @Test
    void computerCanStartBeforePlayer() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.HARD, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void randomComputerGameStartUsesInjectedRandomness() throws Exception {
        game = new Game(new Random() {
            @Override
            public boolean nextBoolean() {
                return false;
            }
        });

        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.RANDOM, QuestionMode.PRESET);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void computerGameRejectsBlankUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startComputerGame("  ", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET));
    }

    @Test
    void computerGameRejectsReservedComputerUsername() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startComputerGame("AI", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET));
    }

    @Test
    void askingComputerRecordsQuestionAndAnswer() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Sam"));

        String answer = game.askComputer("Is your character's eye colour green?");

        assertEquals("Yes", answer);
        assertEquals(1, game.getFirstPlayer().getQuestionsAsked().size());
        assertTrue(game.getFirstPlayer().getQuestionAnswers().get(0));
        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void gameOwnsComputerQuestionAndAnswerFlow() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        Question question = game.playComputerQuestion();
        game.answerComputerQuestion(false);

        assertSame(question, game.getComputerPlayer().getQuestionsAsked().get(0));
        assertFalse(game.getComputerPlayer().getQuestionAnswers().get(0));
        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getComputerPlayer().getIsTurn());
    }

    @Test
    void postGameReviewIsEmptyWhenEveryComputerAnswerWasCorrect() throws Exception {
        recordComputerAnswerFor("Sam", true);

        assertTrue(game.getComputerAnswerCorrections().isEmpty());
    }

    @Test
    void postGameReviewReturnsTheExpectedAnswerForEachMistake() throws Exception {
        Question question = recordComputerAnswerFor("Sam", false);

        assertEquals(
                List.of(new AnswerCorrection(question.getQuestion(), true)),
                game.getComputerAnswerCorrections());
    }

    @Test
    void postGameReviewIsRepeatableAndImmutable() throws Exception {
        Question question = recordComputerAnswerFor("Sam", false);

        List<AnswerCorrection> firstReview = game.getComputerAnswerCorrections();
        List<AnswerCorrection> secondReview = game.getComputerAnswerCorrections();

        assertEquals(firstReview, secondReview);
        assertThrows(
                UnsupportedOperationException.class,
                () -> firstReview.add(new AnswerCorrection(question.getQuestion(), true)));
    }

    @Test
    void completedComputerGameResultContainsBothParticipantHistories() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        game.getFirstPlayer().recordQuestionAnswer("Does your character wear glasses?", true);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Nick"));
        game.getComputerPlayer().recordQuestionAnswer(
                "Is your character's eye colour blue?", false);
        game.finish("AI");
        game.selectCharacter("Player", "Olivia");

        GameResult result = game.getGameResult();

        assertEquals(new GameResult(
                List.of(
                        new GameResult.Participant(
                                "Player",
                                "Olivia",
                                List.of(new GameResult.QuestionAnswer(
                                        "Does your character wear glasses?", true))),
                        new GameResult.Participant(
                                "AI",
                                "Nick",
                                List.of(new GameResult.QuestionAnswer(
                                        "Is your character's eye colour blue?", false)))),
                "AI",
                GameMode.PVE,
                ComputerDifficulty.EASY, QuestionMode.PRESET), result);
    }

    @Test
    void resultRecordsTheQuestionModeTheGameStartedWith() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.FREE_FORM);
        game.finish("Player 1");

        assertEquals(QuestionMode.FREE_FORM, game.getGameResult().questionMode());
    }

    @Test
    void completedPlayerGameResultContainsBothHumanParticipants() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.getFirstPlayer().recordQuestionAnswer("Does the person have visible teeth?", true);
        game.getSecondPlayer().recordQuestionAnswer("Is the person wearing a hat?", false);
        game.finish("Player 2");
        game.selectCharacter("Player 1", "Olivia");
        game.selectCharacter("Player 2", "Nick");

        GameResult result = game.getGameResult();

        assertEquals(List.of("Player 1", "Player 2"), result.participants().stream()
                .map(GameResult.Participant::name)
                .toList());
        assertEquals("Olivia", result.participants().get(0).selectedCharacter());
        assertEquals("Nick", result.participants().get(1).selectedCharacter());
        assertEquals("Player 2", result.winner());
    }

    @Test
    void gameResultIsUnavailableBeforeGameFinishes() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class, () -> game.getGameResult());
    }

    @Test
    void computerAnswerReviewIsUnavailableBeforeGameFinishes() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.getComputerAnswerCorrections());
    }

    @Test
    void computerAnswerReviewIsUnavailableForPlayerGames() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.finish("Player 1");

        assertThrows(
                IllegalStateException.class,
                () -> game.getComputerAnswerCorrections());
    }

    @Test
    void gameProvidesComputerSelectedCharacterIndexAfterComputerGame() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Sam"));
        game.finish("Player");

        assertEquals(0, game.getComputerSelectedCharacterIndex());
    }

    @Test
    void computerSelectedCharacterIndexIsHiddenUntilGameFinishes() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.getComputerSelectedCharacterIndex());
    }

    @Test
    void computerGuessNameIsAvailableWhenOneCandidateRemains() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        leaveOnlyComputerCandidate("Sam");

        assertEquals("Sam", game.getComputerGuessName().orElseThrow());
    }

    @Test
    void computerGuessNameIsEmptyWhileSeveralCandidatesRemain() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertTrue(game.getComputerGuessName().isEmpty());
    }

    @Test
    void computerGuessNameCannotBeReadDuringPlayerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        leaveOnlyComputerCandidate("Sam");

        assertThrows(IllegalStateException.class, () -> game.getComputerGuessName());
    }

    @Test
    void pendingComputerQuestionMustBeAnsweredBeforeReadingGuessName() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        game.playComputerQuestion();
        leaveOnlyComputerCandidate("Sam");

        assertThrows(IllegalStateException.class, () -> game.getComputerGuessName());
    }

    @Test
    void pendingComputerQuestionMustBeAnsweredBeforeResolvingGuess() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        game.playComputerQuestion();
        leaveOnlyComputerCandidate("Sam");

        assertThrows(IllegalStateException.class, () -> game.resolveComputerGuess(true));
    }

    @Test
    void correctComputerGuessFinishesWithComputerAsWinner() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        leaveOnlyComputerCandidate("Sam");

        String winner = game.resolveComputerGuess(true);

        assertEquals("AI", winner);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("AI", game.getWinner().orElseThrow());
    }

    @Test
    void incorrectComputerGuessFinishesWithHumanAsWinner() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        leaveOnlyComputerCandidate("Sam");

        String winner = game.resolveComputerGuess(false);

        assertEquals("Player", winner);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player", game.getWinner().orElseThrow());
    }

    @Test
    void computerGuessCannotBeResolvedBeforeOneCandidateRemains() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class, () -> game.resolveComputerGuess(true));
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void computerQuestionCannotBePlayedInAPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());
    }

    @Test
    void computerAnswerCannotBeRecordedBeforeAQuestion() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class, () -> game.answerComputerQuestion(true));
    }

    @Test
    void computerMustAnswerPendingQuestionBeforePlayingAnother() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        game.playComputerQuestion();

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());

        assertEquals(1, game.getComputerPlayer().getQuestionsAsked().size());
    }

    @Test
    void turnCannotAdvanceWhileComputerQuestionIsPending() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        game.playComputerQuestion();

        assertThrows(IllegalStateException.class, () -> game.advanceTurn());

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getComputerPlayer().getIsTurn());
        assertEquals(1, game.getComputerPlayer().getQuestionsAsked().size());
        assertTrue(game.getComputerPlayer().getQuestionAnswers().isEmpty());
    }

    @Test
    void computerCannotReuseAnAnsweredQuestionOnALaterTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        game.playComputerQuestion();
        game.answerComputerQuestion(false);
        game.advanceTurn();

        assertThrows(IllegalStateException.class, () -> game.answerComputerQuestion(true));

        assertEquals(1, game.getComputerPlayer().getQuestionAnswers().size());
    }

    @Test
    void computerCannotPlayAfterAllQuestionsAreExhausted() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        int availableQuestions = game.getComputerPlayer().getUnAskedQuestions().size();
        for (int index = 0; index < availableQuestions; index++) {
            game.playComputerQuestion();
            game.answerComputerQuestion(false);
            game.advanceTurn();
        }

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());
    }

    @Test
    void playerCannotAskComputerDuringComputerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.askComputer("Is your character's eye colour green?"));

        assertTrue(game.getFirstPlayer().getQuestionsAsked().isEmpty());
    }

    @Test
    void computerCannotPlayQuestionDuringPlayerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class, () -> game.playComputerQuestion());

        assertTrue(game.getComputerPlayer().getQuestionsAsked().isEmpty());
    }

    @Test
    void correctComputerGuessFinishesGameWithPlayerAsWinner() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Sam"));

        String result = game.guessComputer("Sam");

        assertTrue(result.contains("you won"));
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player", game.getWinner().orElseThrow());
    }

    @Test
    void incorrectComputerGuessFinishesGameWithComputerAsWinner() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        game.getComputerPlayer().setSelectedCharacter(
                game.getComputerPlayer().findCharacter("Sam"));

        String result = game.guessComputer("Olivia");

        assertTrue(result.contains("you lost"));
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("AI", game.getWinner().orElseThrow());
    }

    @Test
    void computerGuessCannotBeMadeBeforeGameStarts() {
        assertThrows(IllegalStateException.class, () -> game.guessComputer("Sam"));
    }

    @Test
    void computerGuessCannotBeMadeInAPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class, () -> game.guessComputer("Sam"));
    }

    @Test
    void playerCannotGuessComputerDuringComputerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertThrows(IllegalStateException.class, () -> game.guessComputer("Sam"));

        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void computerGuessRejectsBlankNameWithoutFinishingGame() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalArgumentException.class, () -> game.guessComputer("  "));

        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void correctPlayerGuessFinishesGameWithGuesserAsWinner() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        String winner = game.resolvePlayerGuess("Player 1", "Sam", true);

        assertEquals("Player 1", winner);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player 1", game.getWinner().orElseThrow());
    }

    @Test
    void incorrectPlayerGuessFinishesGameWithOpponentAsWinner() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.SECOND_PLAYER, QuestionMode.PRESET);

        String winner = game.resolvePlayerGuess("Player 2", "Olivia", false);

        assertEquals("Player 1", winner);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player 1", game.getWinner().orElseThrow());
    }

    @Test
    void playerGuessCannotBeResolvedInAComputerGame() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.resolvePlayerGuess("Player", "Sam", true));
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void inactivePlayerCannotResolveAGuess() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.resolvePlayerGuess("Player 2", "Sam", true));
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void unknownCharacterGuessDoesNotFinishGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalArgumentException.class,
                () -> game.resolvePlayerGuess("Player 1", "Unknown character", true));
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void unknownPlayerCannotResolveAGuess() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalArgumentException.class,
                () -> game.resolvePlayerGuess("Unknown", "Sam", true));
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void finishedPlayerGuessCannotBeResolvedAgain() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.resolvePlayerGuess("Player 1", "Sam", true);

        assertThrows(
                IllegalStateException.class,
                () -> game.resolvePlayerGuess("Player 1", "Olivia", false));
        assertEquals("Player 1", game.getWinner().orElseThrow());
    }

    @Test
    void youngerPlayerStartsWhenBirthdayDeterminesTurn() throws Exception {
        game.startPlayerGame(
                "Younger", 20050101,
                "Older", 19950101,
                PlayerGameStart.YOUNGER, QuestionMode.PRESET);

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getSecondPlayer().getIsTurn());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
    }

    @Test
    void firstPlayerCanStartPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void secondPlayerCanStartPlayerGame() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.SECOND_PLAYER, QuestionMode.PRESET);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void playerLookupReturnsPlayerWithMatchingUsername() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertSame(game.getSecondPlayer(), game.getPlayer("Player 2"));
    }

    @Test
    void playerLookupRejectsUnknownUsername() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalArgumentException.class, () -> game.getPlayer("Unknown"));
    }

    @Test
    void gameProvidesCharacterNamesForChoices() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        String[] characterNames = game.getCharacterNames();

        assertArrayEquals(new String[] {
                "Sam", "Olivia", "Nick", "David", "Sofia", "Liz",
                "Lily", "Leo", "Emma", "Daniel", "Ben", "Katie",
                "Al", "Amy", "Mike", "Gabe", "Farah", "Laura",
                "Jordan", "Eric", "Carmen", "Rachel", "Joe", "Mia"
        }, characterNames);
    }

    @Test
    void characterNamesAreUnavailableBeforeGameStarts() {
        assertThrows(IllegalStateException.class, () -> game.getCharacterNames());
    }

    @Test
    void gameSelectsCharacterForNamedPlayer() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.finish("Player 1");

        game.selectCharacter("Player 2", "Sam");

        assertEquals("Sam", game.getSecondPlayer().getSelectedCharacter().getName());
    }

    @Test
    void gameProvidesSelectedCharacterIndexForNamedPlayer() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.finish("Player 1");
        game.selectCharacter("Player 2", "Sam");

        assertEquals(0, game.getSelectedCharacterIndex("Player 2"));
    }

    @Test
    void selectedCharacterIndexIsHiddenUntilGameFinishes() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.getSelectedCharacterIndex("Player"));
    }

    @Test
    void characterCannotBeSelectedUntilGameFinishes() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        Character originalCharacter = game.getFirstPlayer().getSelectedCharacter();
        String differentCharacterName = originalCharacter.getName().equals("Sam") ? "Olivia" : "Sam";

        assertThrows(
                IllegalStateException.class,
                () -> game.selectCharacter("Player 1", differentCharacterName));
        assertSame(originalCharacter, game.getFirstPlayer().getSelectedCharacter());
    }

    @Test
    void characterSelectionRejectsUnknownUsername() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        game.finish("Player");

        assertThrows(
                IllegalArgumentException.class,
                () -> game.selectCharacter("Unknown", "Sam"));
    }

    @Test
    void characterSelectionRejectsUnknownCharacterWithoutChangingSelection() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.PLAYER, QuestionMode.PRESET);
        game.finish("Player");
        Character originalCharacter = game.getFirstPlayer().getSelectedCharacter();

        assertThrows(
                IllegalArgumentException.class,
                () -> game.selectCharacter("Player", "Unknown character"));
        assertSame(originalCharacter, game.getFirstPlayer().getSelectedCharacter());
    }

    @Test
    void gameRecordsAPlayerQuestionAndAnswer() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        game.recordPlayerQuestion(
                "Player 1", "Does your character look friendly?", false);

        assertEquals("Does your character look friendly?",
                game.getFirstPlayer().getQuestionsAsked().get(0).getQuestion());
        assertFalse(game.getFirstPlayer().getQuestionAnswers().get(0));
    }

    @Test
    void playerQuestionCannotBeRecordedBeforeGameStarts() {
        assertThrows(
                IllegalStateException.class,
                () -> game.recordPlayerQuestion("Player 1", "Is this too early?", true));
    }

    @Test
    void inactivePlayerCannotRecordAQuestion() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.recordPlayerQuestion("Player 2", "Am I out of turn?", true));

        assertTrue(game.getSecondPlayer().getQuestionsAsked().isEmpty());
    }

    @Test
    void advancingTurnSwitchesBetweenHumanPlayers() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        game.advanceTurn();

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getSecondPlayer().getIsTurn());

        game.advanceTurn();

        assertTrue(game.getFirstPlayer().getIsTurn());
        assertFalse(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void gameReportsCurrentPlayerAsTurnsAdvance() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertEquals("Player 1", game.getCurrentPlayerName());

        game.advanceTurn();

        assertEquals("Player 2", game.getCurrentPlayerName());
    }

    @Test
    void gameReportsComputerAsCurrentParticipant() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertEquals("AI", game.getCurrentPlayerName());
    }

    @Test
    void currentPlayerIsUnavailableBeforeGameStarts() {
        assertThrows(IllegalStateException.class, () -> game.getCurrentPlayerName());
    }

    @Test
    void availableQuestionTextsFollowTheCurrentHumanPlayer() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.recordPlayerQuestion(
                "Player 1", "Is your character's eye colour blue?", true);
        game.advanceTurn();

        String[] questionTexts = game.getCurrentPlayerQuestionTexts();

        assertArrayEquals(new String[] {
                "Is your character's eye colour blue?",
                "Is your character's eye colour brown?",
                "Is your character's eye colour green?",
                "Is your character a male?",
                "Does your character have a light skin tone?",
                "Is your character's hair colour black?",
                "Is your character's hair colour brown?",
                "Is your character's hair colour ginger?",
                "Is your character's hair colour Blonde?",
                "Is your character's hair colour white?",
                "Does your character have facial hair?",
                "Does your character wear glasses?",
                "Does the person have visible teeth?",
                "Is the person wearing a hat?",
                "Does the person have short hair?",
                "Does the person have long hair?",
                "Does the person have their hair tied up?",
                "Is the person bald?",
                "Does the person have an ear piercing?"
        }, questionTexts);
    }

    @Test
    void humanQuestionTextsAreUnavailableDuringComputerTurn() throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);

        assertThrows(
                IllegalStateException.class,
                () -> game.getCurrentPlayerQuestionTexts());
    }

    @Test
    void humanNamedAiCanAccessQuestionTextsDuringTheirTurn() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "AI", 20010101,
                PlayerGameStart.SECOND_PLAYER, QuestionMode.PRESET);

        String[] questionTexts = game.getCurrentPlayerQuestionTexts();

        assertEquals("Is your character's eye colour blue?", questionTexts[0]);
    }

    @Test
    void turnCannotAdvanceBeforeGameStarts() {
        assertThrows(IllegalStateException.class, () -> game.advanceTurn());
    }

    @Test
    void randomPlayerGameStartUsesInjectedRandomness() throws Exception {
        game = new Game(new Random() {
            @Override
            public boolean nextBoolean() {
                return false;
            }
        });

        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.RANDOM, QuestionMode.PRESET);

        assertFalse(game.getFirstPlayer().getIsTurn());
        assertTrue(game.getSecondPlayer().getIsTurn());
    }

    @Test
    void playerGameRejectsBlankUsernames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startPlayerGame(
                        "  ", 20000101,
                        "Player 2", 20010101,
                        PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startPlayerGame(
                        "Player 1", 20000101,
                        "  ", 20010101,
                        PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET));
    }

    @Test
    void playerGameRejectsDuplicateUsernames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> game.startPlayerGame(
                        "Player", 20000101,
                        "Player", 20010101,
                        PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET));
    }

    @Test
    void finishingGameRecordsWinnerAndStatusTogether() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        game.finish("Player 2");

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player 2", game.getWinner().orElseThrow());
    }

    @Test
    void finishedGameKeepsOriginalWinnerWhenFinishIsCalledAgain() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        game.finish("Player 1");

        assertThrows(IllegalStateException.class, () -> game.finish("Player 2"));

        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals("Player 1", game.getWinner().orElseThrow());
    }

    @Test
    void finishingGameRejectsUnknownWinnerWithoutChangingLifecycle() throws Exception {
        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20010101,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);

        assertThrows(IllegalArgumentException.class, () -> game.finish("Unknown"));

        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void gameCannotFinishBeforeItStarts() {
        assertThrows(IllegalStateException.class, () -> game.finish("Player"));

        assertEquals(GameStatus.STARTING, game.getStatus());
        assertTrue(game.getWinner().isEmpty());
    }

    @Test
    void birthdayTieStillSelectsExactlyOneStartingPlayer() throws Exception {
        game = new Game(new Random() {
            @Override
            public boolean nextBoolean() {
                return true;
            }
        });

        game.startPlayerGame(
                "Player 1", 20000101,
                "Player 2", 20000101,
                PlayerGameStart.YOUNGER, QuestionMode.PRESET);

        assertNotEquals(game.getFirstPlayer().getIsTurn(), game.getSecondPlayer().getIsTurn());
        assertTrue(game.getFirstPlayer().getIsTurn());
    }

    private void leaveOnlyComputerCandidate(String remainingName) {
        for (Character character : game.getComputerPlayer().getPossibleCharacters()) {
            character.setIsActive(character.getName().equals(remainingName));
        }
    }

    private Question recordComputerAnswerFor(String characterName, boolean matchingAnswer)
            throws Exception {
        game.startComputerGame("Player", ComputerDifficulty.EASY, ComputerGameStart.COMPUTER, QuestionMode.PRESET);
        Question question = game.getComputerPlayer().getGameBoard()
                .findQuestion("Is your character's eye colour green?");
        game.getComputerPlayer().setQuestionAsked(question.getQuestion());
        game.getComputerPlayer().addQuestionAnswers(matchingAnswer);
        game.finish("Player");
        game.selectCharacter("Player", characterName);
        return question;
    }
}
