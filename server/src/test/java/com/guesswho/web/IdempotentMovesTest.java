package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class IdempotentMovesTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String asker;
    private String answerer;
    private String code;

    @BeforeEach
    void aGameReadyToPlay() throws Exception {
        jdbcTemplate.update("DELETE FROM room_move_keys");
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");
        String host = signUpAndIn("host", "a-good-password");
        String guest = signUpAndIn("guest", "a-good-password");
        code = codeFrom(mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + host)));
        mockMvc.perform(post("/api/rooms/" + code + "/players")
                .header("Authorization", "Bearer " + guest));
        choose(host, "Olivia");
        choose(guest, "Sam");
        boolean hostFirst = body(state(host)).contains("\"yourTurn\":true");
        asker = hostFirst ? host : guest;
        answerer = hostFirst ? guest : host;
    }

    @Test
    void doesNotAskTheSameQuestionTwiceWhenARequestIsRetried() throws Exception {
        //The failure this exists for: the response is lost on the way back, the
        //client retries because nothing appeared to happen, and the question is
        //recorded twice. Answered first, so the question is in the history
        //where a duplicate would be visible.
        ask("a-move-key", "Does your character wear glasses?");
        answer("answer-key", true);

        ask("a-move-key", "Does your character wear glasses?");

        assertEquals(1, answeredQuestions(),
                "A retried request asked the same question twice");
    }

    @Test
    void answersTheRetryWithWhatTheFirstAttemptDid() throws Exception {
        ask("a-move-key", "Does your character wear glasses?");

        mockMvc.perform(askRequest("a-move-key", "Does your character wear glasses?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourUnansweredQuestion")
                        .value("Does your character wear glasses?"));
    }

    @Test
    void doesNotPassTheTurnTwiceOnARetriedAnswer() throws Exception {
        //Passing the turn twice hands it back to the player who just moved,
        //which is a game neither person can explain.
        ask("ask-key", "Does your character wear glasses?");
        answer("answer-key", true);
        String afterFirst = body(state(asker));

        answer("answer-key", true);

        assertEquals(afterFirst.contains("\"yourTurn\":true"),
                body(state(asker)).contains("\"yourTurn\":true"));
    }

    @Test
    void treatsADifferentKeyAsADifferentMove() throws Exception {
        ask("first-key", "Does your character wear glasses?");
        answer("answer-key", true);

        //A genuine second question, from the other player, with its own key.
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .header("Authorization", "Bearer " + answerer)
                        .header(RoomController.MOVE_KEY_HEADER, "second-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Is the person wearing a hat?\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void scopesKeysToTheirOwnRoom() throws Exception {
        //Two players in different games choosing the same key is nobody's
        //problem but their own room's.
        String other = signUpAndIn("other", "a-good-password");
        String secondCode = codeFrom(mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + other)));
        String fourth = signUpAndIn("fourth", "a-good-password");
        mockMvc.perform(post("/api/rooms/" + secondCode + "/players")
                .header("Authorization", "Bearer " + fourth));

        ask("shared-key", "Does your character wear glasses?");

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_move_keys WHERE move_key = 'shared-key'",
                Integer.class));
    }

    @Test
    void stillWorksForAClientThatSendsNoKey() throws Exception {
        //A request without one cannot be recognised as a retry anyway, so
        //refusing it would turn a missing header into a lost move.
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .header("Authorization", "Bearer " + asker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yourUnansweredQuestion")
                        .value("Does your character wear glasses?"));
    }

    @Test
    void doesNotRecordAKeyForAMoveTheRulesRefused() throws Exception {
        //Out of turn, so nothing happened. Keeping the key would mean the
        //player could never make that move, having used up its only chance.
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .header("Authorization", "Bearer " + answerer)
                        .header(RoomController.MOVE_KEY_HEADER, "refused-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isConflict());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_move_keys WHERE move_key = 'refused-key'",
                Integer.class));
    }

    @Test
    void forgetsARoomsKeysWhenTheRoomIsSweptAway() throws Exception {
        ask("a-move-key", "Does your character wear glasses?");
        jdbcTemplate.update("UPDATE game_rooms SET expires_at = ? WHERE code = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), code);

        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_move_keys", Integer.class);
        sweep();

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_move_keys", Integer.class),
                "Keys left behind by a swept room accumulate for ever");
    }

    // --- helpers -------------------------------------------------------

    @Autowired
    private RoomService roomService;

    private void sweep() {
        roomService.sweepExpired();
    }

    /** How many questions the asker has in their answered history. */
    private int answeredQuestions() throws Exception {
        //Only answered questions reach yourQuestions; a waiting one is
        //reported separately, which is what an earlier version of this helper
        //miscounted.
        return body(state(asker)).split("\"question\":", -1).length - 1;
    }

    private void ask(String key, String question) {
        try {
            mockMvc.perform(askRequest(key, question));
        }
        catch (Exception failed) {
            throw new AssertionError(failed);
        }
    }

    private org.springframework.test.web.servlet.RequestBuilder askRequest(
            String key, String question) {
        return post("/api/rooms/" + code + "/questions")
                .header("Authorization", "Bearer " + asker)
                .header(RoomController.MOVE_KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"%s\"}".formatted(question));
    }

    private void answer(String key, boolean answer) throws Exception {
        mockMvc.perform(post("/api/rooms/" + code + "/answers")
                .header("Authorization", "Bearer " + answerer)
                .header(RoomController.MOVE_KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\": %s}".formatted(answer)));
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
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"character\": \"%s\"}".formatted(character)));
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
