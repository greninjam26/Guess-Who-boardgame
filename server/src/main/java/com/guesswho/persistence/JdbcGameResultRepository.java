package com.guesswho.persistence;

import com.guesswho.game.CharacterCommitment;
import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.game.QuestionMode;

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
    private static final String FIND_PAGE_SQL = """
            SELECT
                game_result.id AS game_result_id,
                game_result.created_at,
                game_result.winner,
                game_result.mode,
                game_result.difficulty,
                game_result.question_mode,
                participant.id AS participant_id,
                participant.name AS participant_name,
                participant.selected_character,
                participant.commitment_hash,
                participant.commitment_nonce,
                question_answer.question,
                question_answer.answer
            FROM (
                SELECT id, created_at, winner, mode, difficulty, question_mode
                FROM game_results
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
            ) game_result
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
        save(gameResult, null);
    }

    @Override
    @Transactional
    public void save(GameResult gameResult, Long accountId) {
        //Only the first participant belongs to the account: the second is
        //whoever else was sitting there, and the computer belongs to nobody.
        Map<String, Long> owner = new java.util.HashMap<>();
        if (!gameResult.participants().isEmpty()) {
            owner.put(gameResult.participants().get(0).name(), accountId);
        }
        saveOwnedBy(gameResult, owner);
    }

    @Override
    @Transactional
    public void saveOwnedBy(GameResult gameResult, Map<String, Long> accountsByParticipantName) {
        long gameResultId = insertGameResult(gameResult);
        for (int playOrder = 0; playOrder < gameResult.participants().size(); playOrder++) {
            GameResult.Participant participant = gameResult.participants().get(playOrder);
            //A participant nobody claimed is stored unattributed rather than
            //refused, so that naming one account stays as easy as naming both
            //and a guest game keeps working unchanged.
            Long owner = accountsByParticipantName.get(participant.name());
            long participantId = insertParticipant(gameResultId, playOrder, participant, owner);
            insertQuestionAnswers(participantId, participant.questionAnswers());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredGameResult> findPage(int limit, int offset) {
        return jdbcTemplate.query(FIND_PAGE_SQL, resultSet -> {
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
                            difficultyOf(resultSet.getString("difficulty")),
                            questionModeOf(resultSet.getString("question_mode")));
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
                            resultSet.getString("selected_character"),
                            commitmentOf(
                                    resultSet.getString("commitment_hash"),
                                    resultSet.getString("commitment_nonce")));
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
        }, limit, offset);
    }

    private long insertGameResult(GameResult gameResult) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO game_results (winner, mode, difficulty, question_mode)\n"
                            + "VALUES (?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, gameResult.winner());
            statement.setString(2, gameResult.mode().name());
            statement.setString(3, gameResult.difficulty() == null
                    ? null
                    : gameResult.difficulty().name());
            statement.setString(4, gameResult.questionMode() == null
                    ? null
                    : gameResult.questionMode().name());
            return statement;
        }, keyHolder);
        return generatedId(keyHolder);
    }

    private long insertParticipant(
            long gameResultId,
            int playOrder,
            GameResult.Participant participant,
            Long accountId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO game_result_participants
                        (game_result_id, play_order, name, selected_character,
                         commitment_hash, commitment_nonce, account_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[] {"id"});
            statement.setLong(1, gameResultId);
            statement.setInt(2, playOrder);
            statement.setString(3, participant.name());
            statement.setString(4, participant.selectedCharacter());
            statement.setString(5, participant.commitment() == null
                    ? null
                    : participant.commitment().hash());
            statement.setString(6, participant.commitment() == null
                    ? null
                    : participant.commitment().nonce());
            if (accountId == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            }
            else {
                statement.setLong(7, accountId);
            }
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

    private static CharacterCommitment commitmentOf(String hash, String nonce) {
        return hash == null || nonce == null ? null : new CharacterCommitment(hash, nonce);
    }

    private static QuestionMode questionModeOf(String storedValue) {
        return storedValue == null ? null : QuestionMode.valueOf(storedValue);
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
        private final QuestionMode questionMode;
        private final Map<Long, ParticipantAccumulator> participants = new LinkedHashMap<>();

        private GameResultAccumulator(
                long id,
                LocalDateTime createdAt,
                String winner,
                GameMode mode,
                ComputerDifficulty difficulty,
                QuestionMode questionMode) {
            this.id = id;
            this.createdAt = createdAt;
            this.winner = winner;
            this.mode = mode;
            this.difficulty = difficulty;
            this.questionMode = questionMode;
        }

        private StoredGameResult toStoredGameResult() {
            List<GameResult.Participant> savedParticipants = participants.values().stream()
                    .map(ParticipantAccumulator::toParticipant)
                    .toList();
            return new StoredGameResult(
                    id,
                    createdAt,
                    new GameResult(savedParticipants, winner, mode, difficulty, questionMode));
        }
    }

    private static final class ParticipantAccumulator {
        private final String name;
        private final String selectedCharacter;
        private final CharacterCommitment commitment;
        private final List<GameResult.QuestionAnswer> questionAnswers = new ArrayList<>();

        private ParticipantAccumulator(
                String name, String selectedCharacter, CharacterCommitment commitment) {
            this.name = name;
            this.selectedCharacter = selectedCharacter;
            this.commitment = commitment;
        }

        private GameResult.Participant toParticipant() {
            return new GameResult.Participant(
                    name, selectedCharacter, questionAnswers, commitment);
        }
    }
}
