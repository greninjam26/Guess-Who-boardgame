package com.guesswho.persistence;

import com.guesswho.game.GameResult;

import java.sql.PreparedStatement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores completed game snapshots in normalized relational tables.
 */
public class JdbcGameResultRepository implements GameResultRepository {
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
        long gameResultId = insertGameResult(gameResult.winner());
        for (int playOrder = 0; playOrder < gameResult.participants().size(); playOrder++) {
            GameResult.Participant participant = gameResult.participants().get(playOrder);
            long participantId = insertParticipant(gameResultId, playOrder, participant);
            insertQuestionAnswers(participantId, participant.questionAnswers());
        }
    }

    private long insertGameResult(String winner) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO game_results (winner) VALUES (?)",
                    new String[] {"id"});
            statement.setString(1, winner);
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

    private long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated ID");
        }
        return key.longValue();
    }
}
