package com.guesswho.persistence;

import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores completed game snapshots in normalized relational tables.
 */
public class JdbcGameResultRepository
        implements GameResultRepository, GameResultHistoryRepository {
    private static final String FIND_ALL_SQL = """
            SELECT
                game_result.id AS game_result_id,
                game_result.created_at,
                game_result.winner,
                game_result.mode,
                game_result.difficulty,
                participant.id AS participant_id,
                participant.name AS participant_name,
                participant.selected_character,
                question_answer.question,
                question_answer.answer
            FROM game_results game_result
            LEFT JOIN game_result_participants participant
              ON participant.game_result_id = game_result.id
            LEFT JOIN game_result_question_answers question_answer
              ON question_answer.participant_id = participant.id
            ORDER BY
                game_result.created_at DESC,
                game_result.id DESC,
                participant.play_order,
                question_answer.question_order
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a repository using the configured JDBC data source.
     *
     * @param jdbcTemplate JDBC operations for the result database
     */
    public JdbcGameResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void save(GameResult gameResult) {
        long gameResultId = insertGameResult(gameResult);
        for (int playOrder = 0; playOrder < gameResult.participants().size(); playOrder++) {
            GameResult.Participant participant = gameResult.participants().get(playOrder);
            long participantId = insertParticipant(gameResultId, playOrder, participant);
            insertQuestionAnswers(participantId, participant.questionAnswers());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredGameResult> findAll() {
        return jdbcTemplate.query(FIND_ALL_SQL, resultSet -> {
            Map<Long, GameResultAccumulator> games = new LinkedHashMap<>();
            while (resultSet.next()) {
                long gameResultId = resultSet.getLong("game_result_id");
                GameResultAccumulator game = games.get(gameResultId);
                if (game == null) {
                    game = new GameResultAccumulator(
                            gameResultId,
                            resultSet.getTimestamp("created_at").toLocalDateTime(),
                            resultSet.getString("winner"),
                            GameMode.valueOf(resultSet.getString("mode")),
                            difficultyOf(resultSet.getString("difficulty")));
                    games.put(gameResultId, game);
                }

                Long participantId = resultSet.getObject("participant_id", Long.class);
                if (participantId == null) {
                    continue;
                }
                ParticipantAccumulator participant = game.participants.get(participantId);
                if (participant == null) {
                    participant = new ParticipantAccumulator(
                            resultSet.getString("participant_name"),
                            resultSet.getString("selected_character"));
                    game.participants.put(participantId, participant);
                }

                String question = resultSet.getString("question");
                if (question != null) {
                    participant.questionAnswers.add(new GameResult.QuestionAnswer(
                            question,
                            resultSet.getBoolean("answer")));
                }
            }
            return games.values().stream()
                    .map(GameResultAccumulator::toStoredGameResult)
                    .toList();
        });
    }

    private long insertGameResult(GameResult gameResult) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO game_results (winner, mode, difficulty) VALUES (?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, gameResult.winner());
            statement.setString(2, gameResult.mode().name());
            statement.setString(3, gameResult.difficulty() == null
                    ? null
                    : gameResult.difficulty().name());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertParticipant(
            long gameResultId,
            int playOrder,
            GameResult.Participant participant) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO game_result_participants
                        (game_result_id, play_order, name, selected_character)
                    VALUES (?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, gameResultId);
            statement.setInt(2, playOrder);
            statement.setString(3, participant.name());
            statement.setString(4, participant.selectedCharacter());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private void insertQuestionAnswers(
            long participantId,
            java.util.List<GameResult.QuestionAnswer> questionAnswers) {
        for (int questionOrder = 0; questionOrder < questionAnswers.size(); questionOrder++) {
            GameResult.QuestionAnswer questionAnswer = questionAnswers.get(questionOrder);
            jdbcTemplate.update(
                    """
                    INSERT INTO game_result_question_answers
                        (participant_id, question_order, question, answer)
                    VALUES (?, ?, ?, ?)
                    """,
                    participantId,
                    questionOrder,
                    questionAnswer.question(),
                    questionAnswer.answer());
        }
    }

    private static ComputerDifficulty difficultyOf(String storedValue) {
        return storedValue == null ? null : ComputerDifficulty.valueOf(storedValue);
    }

    private long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated ID");
        }
        return key.longValue();
    }

    private static final class GameResultAccumulator {
        private final long id;
        private final LocalDateTime createdAt;
        private final String winner;
        private final GameMode mode;
        private final ComputerDifficulty difficulty;
        private final Map<Long, ParticipantAccumulator> participants = new LinkedHashMap<>();

        private GameResultAccumulator(
                long id,
                LocalDateTime createdAt,
                String winner,
                GameMode mode,
                ComputerDifficulty difficulty) {
            this.id = id;
            this.createdAt = createdAt;
            this.winner = winner;
            this.mode = mode;
            this.difficulty = difficulty;
        }

        private StoredGameResult toStoredGameResult() {
            List<GameResult.Participant> savedParticipants = participants.values().stream()
                    .map(ParticipantAccumulator::toParticipant)
                    .toList();
            return new StoredGameResult(
                    id,
                    createdAt,
                    new GameResult(savedParticipants, winner, mode, difficulty));
        }
    }

    private static final class ParticipantAccumulator {
        private final String name;
        private final String selectedCharacter;
        private final List<GameResult.QuestionAnswer> questionAnswers = new ArrayList<>();

        private ParticipantAccumulator(String name, String selectedCharacter) {
            this.name = name;
            this.selectedCharacter = selectedCharacter;
        }

        private GameResult.Participant toParticipant() {
            return new GameResult.Participant(name, selectedCharacter, questionAnswers);
        }
    }
}
