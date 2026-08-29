package com.guesswho.web;

import com.guesswho.GuessWhoServerApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = GuessWhoServerApplication.class)
@AutoConfigureMockMvc
class GameResultControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearResults() {
        jdbcTemplate.update("DELETE FROM game_results");
    }

    @Test
    void storesSubmittedGameResult() throws Exception {
        mockMvc.perform(post("/api/game-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participants": [
                                    {
                                      "name": "Player 1",
                                      "selectedCharacter": "Olivia",
                                      "questionAnswers": [
                                        {"question": "Does your character wear glasses?", "answer": true}
                                      ]
                                    },
                                    {
                                      "name": "Player 2",
                                      "selectedCharacter": "Nick",
                                      "questionAnswers": [
                                        {"question": "Is your character wearing a hat?", "answer": false}
                                      ]
                                    }
                                  ],
                                  "winner": "Player 1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        assertEquals(1, storedGameCount());
        assertEquals("Player 1", jdbcTemplate.queryForObject(
                "SELECT winner FROM game_results", String.class));
        assertEquals(
                List.of(
                        new ParticipantRow(0, "Player 1", "Olivia"),
                        new ParticipantRow(1, "Player 2", "Nick")),
                jdbcTemplate.query(
                        """
                        SELECT play_order, name, selected_character
                        FROM game_result_participants
                        ORDER BY play_order
                        """,
                        (resultSet, rowNumber) -> new ParticipantRow(
                                resultSet.getInt("play_order"),
                                resultSet.getString("name"),
                                resultSet.getString("selected_character"))));
        assertEquals(
                List.of(
                        new QuestionAnswerRow(0, "Does your character wear glasses?", true),
                        new QuestionAnswerRow(1, "Is your character wearing a hat?", false)),
                jdbcTemplate.query(
                        """
                        SELECT participant.play_order, answer.question, answer.answer
                        FROM game_result_question_answers answer
                        JOIN game_result_participants participant
                          ON participant.id = answer.participant_id
                        ORDER BY participant.play_order, answer.question_order
                        """,
                        (resultSet, rowNumber) -> new QuestionAnswerRow(
                                resultSet.getInt("play_order"),
                                resultSet.getString("question"),
                                resultSet.getBoolean("answer"))));
    }

    @Test
    void rejectsWinnerWhoIsNotAParticipant() throws Exception {
        mockMvc.perform(post("/api/game-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participants": [
                                    {
                                      "name": "Player 1",
                                      "selectedCharacter": "Olivia",
                                      "questionAnswers": []
                                    },
                                    {
                                      "name": "Player 2",
                                      "selectedCharacter": "Nick",
                                      "questionAnswers": []
                                    }
                                  ],
                                  "winner": "Someone else"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, storedGameCount());
    }

    @ParameterizedTest
    @MethodSource("incompleteGameResults")
    void rejectsIncompleteGameResult(String requestBody) throws Exception {
        mockMvc.perform(post("/api/game-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        assertEquals(0, storedGameCount());
    }

    private static Stream<String> incompleteGameResults() {
        return Stream.of(
                """
                        {"participants": [], "winner": "Player"}
                        """,
                """
                        {
                          "participants": [
                            {"name": " ", "selectedCharacter": "Olivia", "questionAnswers": []}
                          ],
                          "winner": " "
                        }
                        """,
                """
                        {
                          "participants": [
                            {"name": "Player", "selectedCharacter": " ", "questionAnswers": []}
                          ],
                          "winner": "Player"
                        }
                        """,
                """
                        {
                          "participants": [
                            {
                              "name": "Player",
                              "selectedCharacter": "Olivia",
                              "questionAnswers": [{"question": " ", "answer": true}]
                            }
                          ],
                          "winner": "Player"
                        }
                        """);
    }

    private int storedGameCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_results", Integer.class);
        return count == null ? 0 : count;
    }

    private record ParticipantRow(int playOrder, String name, String selectedCharacter) {
    }

    private record QuestionAnswerRow(int playOrder, String question, boolean answer) {
    }
}
