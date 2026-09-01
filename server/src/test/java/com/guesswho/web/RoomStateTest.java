package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class RoomStateTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String guestToken;
    private String code;

    @BeforeEach
    void aJoinedGame() throws Exception {
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");
        hostToken = signUpAndIn("host", "a-good-password");
        guestToken = signUpAndIn("guest", "a-good-password");
        code = codeFrom(mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + hostToken)));
        mockMvc.perform(post("/api/rooms/" + code + "/players")
                .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isCreated());
    }

    @Test
    void neverPutsTheOpponentsCharacterAnywhereInTheResponse() throws Exception {
        //The strongest form of this test: not that a particular field is
        //absent, but that the name does not appear anywhere in what is sent.
        //A field added later that leaks it will fail here.
        choose(hostToken, "Olivia");
        choose(guestToken, "Sam");

        String seenByHost = stateBody(hostToken);
        String seenByGuest = stateBody(guestToken);

        assertFalse(seenByHost.contains("Sam"),
                "The host was sent the guest's character: " + seenByHost);
        assertFalse(seenByGuest.contains("Olivia"),
                "The guest was sent the host's character: " + seenByGuest);
    }

    @Test
    void tellsEachPlayerTheirOwnCharacter() throws Exception {
        choose(hostToken, "Olivia");
        choose(guestToken, "Sam");

        state(hostToken).andExpect(jsonPath("$.yourCharacter").value("Olivia"));
        state(guestToken).andExpect(jsonPath("$.yourCharacter").value("Sam"));
    }

    @Test
    void saysWhetherTheOpponentHasChosenButNotWhat() throws Exception {
        state(hostToken).andExpect(jsonPath("$.opponentHasChosen").value(false));

        choose(guestToken, "Sam");

        state(hostToken)
                .andExpect(jsonPath("$.opponentHasChosen").value(true))
                .andExpect(jsonPath("$.opponentCharacter").doesNotExist());
    }

    @Test
    void namesTheOpponentSoAPlayerKnowsWhoTheyArePlaying() throws Exception {
        state(hostToken)
                .andExpect(jsonPath("$.you").value("host"))
                .andExpect(jsonPath("$.opponent").value("guest"));
        state(guestToken)
                .andExpect(jsonPath("$.you").value("guest"))
                .andExpect(jsonPath("$.opponent").value("host"));
    }

    @Test
    void showsOnlyOnePlayerTheirTurn() throws Exception {
        boolean hostToPlay = Boolean.parseBoolean(
                fieldOf(stateBody(hostToken), "yourTurn"));
        boolean guestToPlay = Boolean.parseBoolean(
                fieldOf(stateBody(guestToken), "yourTurn"));

        assertTrue(hostToPlay != guestToPlay, "Exactly one player is to move");
    }

    @Test
    void refusesToShowAGameToSomebodyNotInIt() throws Exception {
        //Told the same thing as somebody with a wrong code: distinguishing
        //them would turn this into a way to discover live games.
        String strangerToken = signUpAndIn("stranger", "a-good-password");

        state(strangerToken).andExpect(status().isNotFound());
    }

    @Test
    void refusesToShowAGameWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/api/rooms/" + code + "/state"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void letsAPlayerChooseOnlyTheirOwnCharacter() throws Exception {
        //The username comes from the token, so there is no request that names
        //somebody else's side to choose for.
        choose(hostToken, "Olivia").andExpect(status().isOk());

        assertEquals("Olivia", fieldOf(stateBody(hostToken), "yourCharacter"));
        assertNull(fieldOf(stateBody(guestToken), "yourCharacter"));
    }

    @Test
    void refusesASecondChoice() throws Exception {
        choose(hostToken, "Olivia").andExpect(status().isOk());

        choose(hostToken, "Sam").andExpect(status().isConflict());
    }

    @Test
    void refusesSomebodyWhoIsNotOnTheBoard() throws Exception {
        choose(hostToken, "Gandalf").andExpect(status().isConflict());
    }

    @Test
    void refusesAnEmptyChoice() throws Exception {
        mockMvc.perform(post("/api/rooms/" + code + "/character")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void showsAWaitingRoomWithNoGameInIt() throws Exception {
        String waiting = codeFrom(mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + hostToken)));

        mockMvc.perform(get("/api/rooms/" + waiting + "/state")
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.opponent").doesNotExist())
                .andExpect(jsonPath("$.yourCharacter").doesNotExist());
    }

    // --- helpers -------------------------------------------------------

    private ResultActions state(String token) throws Exception {
        return mockMvc.perform(get("/api/rooms/" + code + "/state")
                .header("Authorization", "Bearer " + token));
    }

    private String stateBody(String token) throws Exception {
        return state(token).andReturn().getResponse().getContentAsString();
    }

    private ResultActions choose(String token, String character) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + code + "/character")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"character\": \"%s\"}".formatted(character)));
    }

    private static String fieldOf(String json, String field) {
        int at = json.indexOf("\"" + field + "\":");
        if (at < 0) {
            return null;
        }
        int start = at + field.length() + 3;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        String value = json.substring(start, end).replace("\"", "").trim();
        return "null".equals(value) ? null : value;
    }

    private static String codeFrom(ResultActions created) throws Exception {
        String body = created.andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"code\":\"") + 8;
        return body.substring(start, body.indexOf('"', start));
    }

    private String signUpAndIn(String username, String password) throws Exception {
        String credentials =
                "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password);
        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(credentials));
        String body = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"token\":\"") + 9;
        return body.substring(start, body.indexOf('"', start));
    }
}
