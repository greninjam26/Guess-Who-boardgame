package com.guesswho.persistence;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.game.GameResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                "Player");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> gameResultRepository.save(invalidResult));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_results", Integer.class));
    }
}
