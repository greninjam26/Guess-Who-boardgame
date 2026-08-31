package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.ComputerGameStart;
import com.guesswho.game.Game;
import com.guesswho.game.GameSnapshot;
import com.guesswho.game.PlayerGameStart;
import com.guesswho.game.QuestionMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedGameStoreTest {
    @TempDir
    private Path directory;

    private SavedGameStore store;

    @BeforeEach
    void freshStore() {
        store = new SavedGameStore(directory.resolve("saved-game.json"));
    }

    @Test
    void hasNothingToOfferBeforeAnyGameIsSaved() {
        assertTrue(store.read().isEmpty());
        assertFalse(store.hasSavedGame());
    }

    @Test
    void bringsBackAGameAgainstTheComputer() throws Exception {
        Game game = computerGame();
        game.selectCharacter("sam", "Olivia");
        int eliminated = playOneComputerTurn(game);

        store.save(saved(game));
        SavedGame read = store.read().orElseThrow();

        assertEquals("sam", read.game().firstPlayer().username());
        assertEquals("Olivia", read.game().firstPlayer().selectedCharacter());
        assertEquals(ComputerDifficulty.HARD, read.game().computerDifficulty());
        assertEquals(eliminated, read.game().computer().ruledOut().size(),
                "What the computer had worked out must survive the round trip");
    }

    @Test
    void bringsBackATwoPlayerGameWithBothPlayers() throws Exception {
        Game game = new Game();
        game.startPlayerGame("sam", 1, "alex", 2,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.FREE_FORM);

        store.save(saved(game));
        SavedGame read = store.read().orElseThrow();

        assertEquals("alex", read.game().secondPlayer().username());
        assertEquals(QuestionMode.FREE_FORM, read.game().questionMode());
        assertFalse(read.game().isAgainstComputer());
    }

    @Test
    void bringsBackTheCardsThePlayerHadTurnedOver() throws Exception {
        List<Boolean> flipped = faceDown(0, 4, 23);

        store.save(new SavedGame(SavedGame.VERSION, computerGame().snapshot(), true,
                OpeningTurn.FIRST_PLAYER, flipped, faceDown(), "asked", "answered"));
        SavedGame read = store.read().orElseThrow();

        assertEquals(flipped, read.firstBoard(),
                "The flipped cards are the player's working notes and must survive");
        assertEquals("asked", read.firstTranscript());
        assertEquals("answered", read.secondTranscript());
    }

    @Test
    void aSavedGameCanActuallyBeResumed() throws Exception {
        //The round trip that matters: not that the fields survive JSON, but
        //that what comes back is a game that plays.
        Game game = computerGame();
        game.getFirstPlayer().recordQuestionAnswer("Does your character wear glasses?", true);
        playOneComputerTurn(game);
        int stillPossible = game.getComputerPlayer().getPossibleCharacters().size();

        GameSnapshot fromDisk = savedAndReadBack(game);
        Game resumed = Game.restoredFrom(fromDisk);

        assertEquals(1, resumed.getFirstPlayer().getQuestionsAsked().size());
        assertEquals(stillPossible, resumed.getComputerPlayer().getPossibleCharacters().size(),
                "A resumed computer must face the same board it had narrowed down");
        assertTrue(stillPossible < 24, "The turn should have ruled somebody out");
    }

    @Test
    void replacesThePreviousSaveRatherThanKeepingBoth() throws Exception {
        store.save(saved(computerGame()));

        Game later = new Game();
        later.startPlayerGame("ana", 1, "bo", 2,
                PlayerGameStart.FIRST_PLAYER, QuestionMode.PRESET);
        store.save(saved(later));

        assertEquals("ana", store.read().orElseThrow().game().firstPlayer().username());
    }

    @Test
    void forgetsTheGameOnceItIsCleared() throws Exception {
        store.save(saved(computerGame()));

        store.clear();

        assertTrue(store.read().isEmpty(),
                "A finished game offered back would look like the game had not noticed it ended");
    }

    @Test
    void clearingWhenThereIsNothingSavedIsHarmless() {
        store.clear();
        store.clear();
    }

    @Test
    void discardsAFileItCannotParseRatherThanFailing() throws Exception {
        Files.writeString(directory.resolve("saved-game.json"),
                "{ this is not json", StandardCharsets.UTF_8);

        assertTrue(store.read().isEmpty(),
                "An unreadable save must never stop the game starting");
    }

    @Test
    void discardsAnEmptyFile() throws Exception {
        Files.writeString(directory.resolve("saved-game.json"), "", StandardCharsets.UTF_8);

        assertTrue(store.read().isEmpty());
    }

    @Test
    void discardsASaveFromADifferentVersion() throws Exception {
        SavedGame fromTheFuture = new SavedGame(SavedGame.VERSION + 1,
                computerGame().snapshot(), true, OpeningTurn.FIRST_PLAYER,
                faceDown(), faceDown(), "", "");

        store.save(fromTheFuture);

        assertTrue(store.read().isEmpty(),
                "A save whose shape is not known should be dropped, not half understood");
    }

    @Test
    void refusesToThrowWhenTheSaveCannotBeWritten() throws Exception {
        //A directory where the file should be: writing cannot succeed, and the
        //game must carry on regardless.
        Path blocked = directory.resolve("blocked");
        Files.createDirectories(blocked.resolve("saved-game.json"));

        boolean written = new SavedGameStore(blocked.resolve("saved-game.json"))
                .save(saved(computerGame()));

        assertFalse(written);
    }

    // --- helpers -------------------------------------------------------

    /** Plays one computer turn through the public flow, and says how many it ruled out. */
    private static int playOneComputerTurn(Game game) {
        game.getFirstPlayer().setIsTurn(false);
        game.getComputerPlayer().setIsTurn(true);
        game.playComputerQuestion();
        game.answerComputerQuestion(true);
        return 24 - game.getComputerPlayer().getPossibleCharacters().size();
    }

    private GameSnapshot savedAndReadBack(Game game) {
        store.save(saved(game));
        return store.read().orElseThrow().game();
    }

    private static Game computerGame() throws Exception {
        Game game = new Game();
        game.startComputerGame("sam", ComputerDifficulty.HARD,
                ComputerGameStart.PLAYER, QuestionMode.PRESET);
        return game;
    }

    private static SavedGame saved(Game game) {
        return new SavedGame(SavedGame.VERSION, game.snapshot(), true,
                OpeningTurn.FIRST_PLAYER, faceDown(), faceDown(), "", "");
    }

    private static List<Boolean> faceDown(int... flipped) {
        Boolean[] cards = new Boolean[24];
        java.util.Arrays.fill(cards, false);
        for (int index : flipped) {
            cards[index] = true;
        }
        return List.of(cards);
    }

}
