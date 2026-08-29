package com.guesswho.web;

import com.guesswho.GuessWhoServerApplication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = GuessWhoServerApplication.class)
@AutoConfigureMockMvc
class LeaderboardControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearResults() {
        jdbcTemplate.update("DELETE FROM game_results");
    }

    @Test
    void returnsEmptyLeaderboardWhenNoGamesAreStored() throws Exception {
        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void aggregatesGamesAndWinsInLeaderboardOrder() throws Exception {
        submitGame("Alex", "AI", "Alex");
        submitGame("Blake", "Alex", "Blake");
        submitGame("Alex", "Casey", "Alex");

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("Alex"))
                .andExpect(jsonPath("$[0].gamesPlayed").value(3))
                .andExpect(jsonPath("$[0].wins").value(2))
                .andExpect(jsonPath("$[1].name").value("Blake"))
                .andExpect(jsonPath("$[1].gamesPlayed").value(1))
                .andExpect(jsonPath("$[1].wins").value(1))
                .andExpect(jsonPath("$[2].name").value("AI"))
                .andExpect(jsonPath("$[2].wins").value(0))
                .andExpect(jsonPath("$[3].name").value("Casey"))
                .andExpect(jsonPath("$[3].wins").value(0));
    }

    private void submitGame(String firstPlayer, String secondPlayer, String winner)
            throws Exception {
        mockMvc.perform(post("/api/game-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participants": [
                                    {
                                      "name": "%s",
                                      "selectedCharacter": "Olivia",
                                      "questionAnswers": []
                                    },
                                    {
                                      "name": "%s",
                                      "selectedCharacter": "Nick",
                                      "questionAnswers": []
                                    }
                                  ],
                                  "winner": "%s"
                                }
                                """.formatted(firstPlayer, secondPlayer, winner)))
                .andExpect(status().isCreated());
    }
}
