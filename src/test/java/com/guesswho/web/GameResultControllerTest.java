package com.guesswho.web;

import com.guesswho.GuessWhoServerApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = GuessWhoServerApplication.class)
@AutoConfigureMockMvc
class GameResultControllerTest {
    private static final Path RESULTS_FILE = Path.of(
            "target", "test-results", "game-results.csv").toAbsolutePath();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureResultFile(DynamicPropertyRegistry registry) {
        registry.add("guesswho.results.file", RESULTS_FILE::toString);
    }

    @BeforeEach
    void resetResultFile() throws Exception {
        Files.createDirectories(RESULTS_FILE.getParent());
        Files.deleteIfExists(RESULTS_FILE);
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

        String expected = String.join(System.lineSeparator(),
                "Player 1,Olivia,Does your character wear glasses?, yes",
                "Player 2,Nick,Is your character wearing a hat?, no",
                "Player 1",
                "");
        assertEquals(expected, Files.readString(RESULTS_FILE));
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

        assertFalse(Files.exists(RESULTS_FILE));
    }

    @ParameterizedTest
    @MethodSource("incompleteGameResults")
    void rejectsIncompleteGameResult(String requestBody) throws Exception {
        mockMvc.perform(post("/api/game-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        assertFalse(Files.exists(RESULTS_FILE));
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
}
