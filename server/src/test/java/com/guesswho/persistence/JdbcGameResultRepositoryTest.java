package com.guesswho.persistence;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.game.QuestionMode;
import com.guesswho.game.GameResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = GuessWhoServerApplication.class)
class JdbcGameResultRepositoryTest {
    @Autowired
    private GameResultRepository gameResultRepository;

    @Autowired
    private GameResultHistoryRepository gameResultHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearResults() {
        jdbcTemplate.update("DELETE FROM game_results");
    }

    @Test
    void rollsBackWholeGameWhenParticipantCannotBeStored() {
        GameResult invalidResult = new GameResult(
                List.of(
                        new GameResult.Participant("Player", "Olivia", List.of()),
                        new GameResult.Participant("AI", null, List.of())),
                "Player",
                GameMode.PVE,
                ComputerDifficulty.HARD, QuestionMode.PRESET);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> gameResultRepository.save(invalidResult));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_results", Integer.class));
    }

    @Test
    void loadsStoredGamesNewestFirstWithCompleteParticipantHistory() {
        LocalDateTime olderCreatedAt = LocalDateTime.of(2026, 8, 27, 12, 0);
        LocalDateTime newerCreatedAt = LocalDateTime.of(2026, 8, 28, 15, 30);
        insertGame(-10, "Player 1", olderCreatedAt, GameMode.PVP_LOCAL, null, QuestionMode.PRESET);
        insertParticipant(-101, -10, 0, "Player 1", "Olivia");
        insertParticipant(-102, -10, 1, "Player 2", "Nick");
        insertQuestionAnswer(-101, 0, "Does your character wear glasses?", true);
        insertQuestionAnswer(-101, 1, "Is your character wearing a hat?", false);

        insertGame(-20, "AI", newerCreatedAt, GameMode.PVE, ComputerDifficulty.HARD, QuestionMode.PRESET);
        insertParticipant(-201, -20, 0, "Player", "Sam");
        insertParticipant(-202, -20, 1, "AI", "Olivia");
        insertQuestionAnswer(-202, 0, "Does your character have dark hair?", true);

        assertEquals(
                List.of(
                        new StoredGameResult(
                                -20,
                                newerCreatedAt,
                                new GameResult(
                                        List.of(
                                                new GameResult.Participant(
                                                        "Player", "Sam", List.of()),
                                                new GameResult.Participant(
                                                        "AI",
                                                        "Olivia",
                                                        List.of(new GameResult.QuestionAnswer(
                                                                "Does your character have dark hair?",
                                                                true)))),
                                        "AI",
                                        GameMode.PVE,
                                        ComputerDifficulty.HARD, QuestionMode.PRESET)),
                        new StoredGameResult(
                                -10,
                                olderCreatedAt,
                                new GameResult(
                                        List.of(
                                                new GameResult.Participant(
                                                        "Player 1",
                                                        "Olivia",
                                                        List.of(
                                                                new GameResult.QuestionAnswer(
                                                                        "Does your character wear glasses?",
                                                                        true),
                                                                new GameResult.QuestionAnswer(
                                                                        "Is your character wearing a hat?",
                                                                        false))),
                                                new GameResult.Participant(
                                                        "Player 2", "Nick", List.of())),
                                        "Player 1",
                                        GameMode.PVP_LOCAL,
                                        null, QuestionMode.PRESET))),
                gameResultHistoryRepository.findPage(50, 0));
    }

    @Test
    void limitsWholeGamesRatherThanJoinedRows() {
        LocalDateTime olderCreatedAt = LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime newerCreatedAt = LocalDateTime.of(2026, 8, 21, 10, 0);
        insertGame(-10, "Player 1", olderCreatedAt, GameMode.PVP_LOCAL, null, QuestionMode.PRESET);
        insertParticipant(-101, -10, 0, "Player 1", "Olivia");
        insertQuestionAnswer(-101, 0, "Does your character wear glasses?", true);
        insertQuestionAnswer(-101, 1, "Is your character wearing a hat?", false);
        insertGame(-20, "AI", newerCreatedAt, GameMode.PVE, ComputerDifficulty.HARD,
                QuestionMode.PRESET);
        insertParticipant(-201, -20, 0, "Player", "Sam");
        insertParticipant(-202, -20, 1, "AI", "Olivia");
        insertQuestionAnswer(-202, 0, "Does your character have dark hair?", true);

        List<StoredGameResult> firstPage = gameResultHistoryRepository.findPage(1, 0);

        assertEquals(1, firstPage.size());
        assertEquals(-20, firstPage.get(0).id());
        assertEquals(2, firstPage.get(0).gameResult().participants().size(),
                "A limited page must still carry every participant of the games it returns");
    }

    @Test
    void skipsGamesWithAnOffset() {
        LocalDateTime olderCreatedAt = LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime newerCreatedAt = LocalDateTime.of(2026, 8, 21, 10, 0);
        insertGame(-10, "Player 1", olderCreatedAt, GameMode.PVP_LOCAL, null, QuestionMode.PRESET);
        insertParticipant(-101, -10, 0, "Player 1", "Olivia");
        insertGame(-20, "AI", newerCreatedAt, GameMode.PVE, ComputerDifficulty.HARD,
                QuestionMode.PRESET);
        insertParticipant(-201, -20, 0, "Player", "Sam");

        List<StoredGameResult> secondPage = gameResultHistoryRepository.findPage(1, 1);

        assertEquals(1, secondPage.size());
        assertEquals(-10, secondPage.get(0).id());
    }

    private void insertGame(
            long id,
            String winner,
            LocalDateTime createdAt,
            GameMode mode,
            ComputerDifficulty difficulty,
            QuestionMode questionMode) {
        jdbcTemplate.update(
                """
                INSERT INTO game_results
                    (id, winner, created_at, mode, difficulty, question_mode)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                winner,
                createdAt,
                mode.name(),
                difficulty == null ? null : difficulty.name(),
                questionMode == null ? null : questionMode.name());
    }

    private void insertParticipant(
            long id,
            long gameResultId,
            int playOrder,
            String name,
            String selectedCharacter) {
        jdbcTemplate.update(
                """
                INSERT INTO game_result_participants
                    (id, game_result_id, play_order, name, selected_character)
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                gameResultId,
                playOrder,
                name,
                selectedCharacter);
    }

    private void insertQuestionAnswer(
            long participantId,
            int questionOrder,
            String question,
            boolean answer) {
        jdbcTemplate.update(
                """
                INSERT INTO game_result_question_answers
                    (participant_id, question_order, question, answer)
                VALUES (?, ?, ?, ?)
                """,
                participantId,
                questionOrder,
                question,
                answer);
    }
}
