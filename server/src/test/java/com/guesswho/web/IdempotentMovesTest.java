package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guesswho.GuessWhoServerApplication;
import java.util.List;
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
        //Checked, not fired and forgotten. These used to send no move key, and
        //once one became compulsory they answered 400 and neither player ever
        //chose — while every test in the class carried on passing against a
        //game state that cannot occur in play.
        choose(host, "Olivia", "host-chooses").andExpect(status().isOk());
        choose(guest, "Sam", "guest-chooses").andExpect(status().isOk());
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
        //problem but their own room's. Both rooms have to actually use the key
        //for that to mean anything: submitting it in one room and counting the
        //rows proves only that one move was recorded once.
        String other = signUpAndIn("other", "a-good-password");
        String secondCode = codeFrom(mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + other)));
        String fourth = signUpAndIn("fourth", "a-good-password");
        mockMvc.perform(post("/api/rooms/" + secondCode + "/players")
                .header("Authorization", "Bearer " + fourth))
                .andExpect(status().isCreated());
        //A move needs a game that has started, so the second room chooses too.
        chooseIn(secondCode, other, "Olivia", "their-host-choice")
                .andExpect(status().isOk());
        chooseIn(secondCode, fourth, "Sam", "their-guest-choice")
                .andExpect(status().isOk());

        //The same key, used for a real move in each room, and accepted in both.
        mockMvc.perform(askRequest("shared-key", "Does your character wear glasses?"))
                .andExpect(status().isOk());
        String theirMover = body(stateOf(secondCode, other)).contains("\"yourTurn\":true")
                ? other
                : fourth;
        mockMvc.perform(post("/api/rooms/" + secondCode + "/questions")
                        .header("Authorization", "Bearer " + theirMover)
                        .header(RoomController.MOVE_KEY_HEADER, "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isOk());

        //One row per room, which is what scoping means.
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_move_keys WHERE move_key = 'shared-key'",
                Integer.class));
    }

    @Test
    void refusesAMoveWithNoKeyAtAll() throws Exception {
        //Once this project ships the only client, a request without a key is a
        //bug rather than somebody else being awkward — and accepting it loses
        //the guarantee quietly, which is worse than saying so.
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .header("Authorization", "Bearer " + asker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, answeredQuestions());
    }

    @Test
    void refusesABlankKey() throws Exception {
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .header("Authorization", "Bearer " + asker)
                        .header(RoomController.MOVE_KEY_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAKeyWiderThanTheColumn() throws Exception {
        //Otherwise it reaches the database, breaks the column, and comes back
        //as a 500 for what is plainly a bad request.
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .header("Authorization", "Bearer " + asker)
                        .header(RoomController.MOVE_KEY_HEADER, "k".repeat(65))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsAKeyRightUpToTheLimit() throws Exception {
        mockMvc.perform(post("/api/rooms/" + code + "/questions")
                        .header("Authorization", "Bearer " + asker)
                        .header(RoomController.MOVE_KEY_HEADER, "k".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Does your character wear glasses?\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refusesAKeylessMoveOnEveryMoveEndpoint() throws Exception {
        //All four, because one endpoint forgetting the check is exactly how
        //character choice ended up without a key in the first place.
        for (String segment : List.of("character", "questions", "answers", "guesses")) {
            mockMvc.perform(post("/api/rooms/" + code + "/" + segment)
                            .header("Authorization", "Bearer " + asker)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"character\": \"Sam\", \"question\": \"Q?\","
                                    + " \"answer\": true}"))
                    .andExpect(status().isBadRequest());
        }
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

    @Test
    void doesNotRefuseARetriedCharacterChoiceAsChoosingTwice() throws Exception {
        //A response lost on the way back is retried with the same key. Without
        //the key reaching the server, the retry is refused as a second choice
        //and the player is told their choice failed when it had not.
        String code = ownRoom();

        chooseWithKey(code, "Olivia", "choice-key").andExpect(status().isOk());
        chooseWithKey(code, "Olivia", "choice-key").andExpect(status().isOk());
    }

    @Test
    void stillRefusesAGenuineSecondChoice() throws Exception {
        //A different key is a different move, and choosing twice is not allowed.
        String code = ownRoom();

        chooseWithKey(code, "Olivia", "first-key").andExpect(status().isOk());
        chooseWithKey(code, "Sam", "second-key").andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions chooseWithKey(
            String roomCode, String character, String key) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + roomCode + "/character")
                .header("Authorization", "Bearer " + asker)
                .header(RoomController.MOVE_KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"character\": \"%s\"}".formatted(character)));
    }

    /**
     * A newly joined room in which nobody has chosen yet.
     *
     * <p>The room the other tests use already has both characters chosen, and
     * choosing is the thing under test here.</p>
     */
    private String ownRoom() throws Exception {
        String fresh = codeFrom(mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + asker)));
        String opponent = signUpAndIn("opponent" + System.nanoTime(), "a-good-password");
        mockMvc.perform(post("/api/rooms/" + fresh + "/players")
                .header("Authorization", "Bearer " + opponent))
                .andExpect(status().isCreated());
        return fresh;
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
        return stateOf(code, token);
    }

    /** The state of a named room, for tests that need more than the usual one. */
    private ResultActions stateOf(String roomCode, String token) throws Exception {
        return mockMvc.perform(get("/api/rooms/" + roomCode + "/state")
                .header("Authorization", "Bearer " + token));
    }

    /** Chooses a character in a named room. */
    private ResultActions chooseIn(
            String roomCode, String token, String character, String key) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + roomCode + "/character")
                .header("Authorization", "Bearer " + token)
                .header(RoomController.MOVE_KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"character\": \"%s\"}".formatted(character)));
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    /**
     * Chooses a character as one of the two players, carrying a move key.
     *
     * <p>The key is not optional — the endpoint answers 400 without one — so it
     * is a parameter rather than something a caller can leave off.</p>
     */
    private ResultActions choose(String token, String character, String key) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + code + "/character")
                .header("Authorization", "Bearer " + token)
                .header(RoomController.MOVE_KEY_HEADER, key)
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
