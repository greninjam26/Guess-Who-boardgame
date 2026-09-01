package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guesswho.GuessWhoServerApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = GuessWhoServerApplication.class)
@AutoConfigureMockMvc
class OnlineGameTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String host;
    private String guest;
    private String code;

    @BeforeEach
    void aGameWithBothCharactersChosen() throws Exception {
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");
        host = signUpAndIn("host", "a-good-password");
        guest = signUpAndIn("guest", "a-good-password");
        code = codeFrom(mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + host)));
        mockMvc.perform(post("/api/rooms/" + code + "/players")
                .header("Authorization", "Bearer " + guest));
        choose(host, "Olivia");
        choose(guest, "Sam");
    }

    @Test
    void playsAWholeGameThroughTheApi() throws Exception {
        String asker = whoseTurn();
        String answerer = asker.equals("host") ? guest : host;
        String askerToken = asker.equals("host") ? host : guest;

        ask(askerToken, "Does your character wear glasses?").andExpect(status().isOk());
        answer(answerer, true).andExpect(status().isOk());

        //The turn has passed, so the other player asks next.
        ask(answerer, "Is the person wearing a hat?").andExpect(status().isOk());
        answer(askerToken, false).andExpect(status().isOk());
    }

    @Test
    void tellsTheOpponentThereIsAQuestionWaitingForThem() throws Exception {
        String asker = whoseTurn();
        String askerToken = asker.equals("host") ? host : guest;
        String otherToken = asker.equals("host") ? guest : host;

        ask(askerToken, "Does your character wear glasses?");

        state(otherToken).andExpect(
                jsonPath("$.questionAwaitingYourAnswer").value("Does your character wear glasses?"));
        state(askerToken)
                .andExpect(jsonPath("$.questionAwaitingYourAnswer").doesNotExist())
                .andExpect(jsonPath("$.yourUnansweredQuestion")
                        .value("Does your character wear glasses?"));
    }

    @Test
    void refusesAQuestionOutOfTurn() throws Exception {
        String waiting = whoseTurn().equals("host") ? guest : host;

        ask(waiting, "Does your character wear glasses?").andExpect(status().isConflict());
    }

    @Test
    void refusesToLetAPlayerAnswerTheirOwnQuestion() throws Exception {
        String askerToken = whoseTurn().equals("host") ? host : guest;
        ask(askerToken, "Does your character wear glasses?");

        answer(askerToken, true).andExpect(status().isConflict());
    }

    @Test
    void refusesASecondQuestionBeforeTheFirstIsAnswered() throws Exception {
        String askerToken = whoseTurn().equals("host") ? host : guest;
        ask(askerToken, "Does your character wear glasses?");

        ask(askerToken, "Is the person wearing a hat?").andExpect(status().isConflict());
    }

    @Test
    void decidesAGuessItselfRatherThanAskingTheLoserToAgree() throws Exception {
        //The opponent's character is on the server. Asking the player who just
        //lost to confirm that they lost is not a check worth having.
        String askerToken = whoseTurn().equals("host") ? host : guest;
        String theirOpponentsCharacter = whoseTurn().equals("host") ? "Sam" : "Olivia";

        guess(askerToken, theirOpponentsCharacter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.winner").value(whoseTurnName()));
    }

    @Test
    void givesTheGameToTheOtherPlayerWhenAGuessIsWrong() throws Exception {
        String askerToken = whoseTurn().equals("host") ? host : guest;
        String otherName = whoseTurn().equals("host") ? "guest" : "host";

        guess(askerToken, "Nick")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner").value(otherName));
    }

    @Test
    void neverLeaksTheOpponentsCharacterWhileTheGameIsPlayed() throws Exception {
        //Repeated after moves, because the leak that matters is the one a
        //later field introduces.
        String askerToken = whoseTurn().equals("host") ? host : guest;
        String otherToken = whoseTurn().equals("host") ? guest : host;
        ask(askerToken, "Does your character wear glasses?");
        answer(otherToken, true);

        assertFalse(body(state(host)).contains("Sam"), body(state(host)));
        assertFalse(body(state(guest)).contains("Olivia"), body(state(guest)));
    }

    @Test
    void refusesAMoveFromSomebodyNotInTheGame() throws Exception {
        String stranger = signUpAndIn("stranger", "a-good-password");

        ask(stranger, "Does your character wear glasses?").andExpect(status().isNotFound());
        guess(stranger, "Sam").andExpect(status().isNotFound());
    }

    @Test
    void refusesAMoveWithoutSigningIn() throws Exception {
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAnEmptyQuestionOrAnswer() throws Exception {
        String askerToken = whoseTurn().equals("host") ? host : guest;

        ask(askerToken, "  ").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/rooms/" + code + "/answers")
                        .header("Authorization", "Bearer " + askerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers -------------------------------------------------------

    private String whoseTurn() throws Exception {
        return body(state(host)).contains("\"yourTurn\":true") ? "host" : "guest";
    }

    private String whoseTurnName() throws Exception {
        return whoseTurn();
    }

    private ResultActions state(String token) throws Exception {
        return mockMvc.perform(get("/api/rooms/" + code + "/state")
                .header("Authorization", "Bearer " + token));
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private ResultActions choose(String token, String character) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + code + "/character")
                .header("Authorization", "Bearer " + token)
                .header(RoomController.MOVE_KEY_HEADER, aFreshKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"character\": \"%s\"}".formatted(character)));
    }

    private ResultActions ask(String token, String question) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + code + "/questions")
                .header("Authorization", "Bearer " + token)
                .header(RoomController.MOVE_KEY_HEADER, aFreshKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"%s\"}".formatted(question)));
    }

    private ResultActions answer(String token, boolean answer) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + code + "/answers")
                .header("Authorization", "Bearer " + token)
                .header(RoomController.MOVE_KEY_HEADER, aFreshKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\": %s}".formatted(answer)));
    }

    private ResultActions guess(String token, String character) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + code + "/guesses")
                .header("Authorization", "Bearer " + token)
                .header(RoomController.MOVE_KEY_HEADER, aFreshKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"character\": \"%s\"}".formatted(character)));
    }

    /** A key per call, which is what a client generates for each move. */
    private static String aFreshKey() {
        return java.util.UUID.randomUUID().toString();
    }

    private static String codeFrom(ResultActions created) throws Exception {
        String created_body = created.andReturn().getResponse().getContentAsString();
        int start = created_body.indexOf("\"code\":\"") + 8;
        return created_body.substring(start, created_body.indexOf('"', start));
    }

    private String signUpAndIn(String username, String password) throws Exception {
        String credentials =
                "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password);
        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(credentials));
        String signedIn = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn().getResponse().getContentAsString();
        int start = signedIn.indexOf("\"token\":\"") + 9;
        return signedIn.substring(start, signedIn.indexOf('"', start));
    }
}
