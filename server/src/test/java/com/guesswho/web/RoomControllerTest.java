package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.persistence.RoomRepository;
import com.guesswho.room.RoomCode;
import com.guesswho.room.RoomStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class RoomControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RoomService roomService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String hostToken;
    private String guestToken;

    @BeforeEach
    void twoSignedInPlayers() throws Exception {
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");
        hostToken = signUpAndIn("host", "a-good-password");
        guestToken = signUpAndIn("guest", "a-good-password");
    }

    @Test
    void opensARoomWithAShareableCode() throws Exception {
        createRoom(hostToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.hostName").value("host"))
                .andExpect(jsonPath("$.guestName").doesNotExist());
    }

    @Test
    void givesEachRoomItsOwnCode() throws Exception {
        assertNotEquals(codeFrom(createRoom(hostToken)), codeFrom(createRoom(hostToken)));
    }

    @Test
    void refusesToOpenARoomWithoutSigningIn() throws Exception {
        //Local play is open to guests; an online game has to know who is on
        //each side, or a stranger can act as either player.
        mockMvc.perform(post("/api/rooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void letsSomebodyElseJoinWithTheCode() throws Exception {
        String code = codeFrom(createRoom(hostToken));

        join(code, guestToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.guestName").value("guest"));
    }

    @Test
    void startsTheGameWhenTheSecondPlayerArrives() throws Exception {
        String code = codeFrom(createRoom(hostToken));
        join(code, guestToken).andExpect(status().isCreated());

        RoomRepository.StoredRoom room = rooms.findByCode(code).orElseThrow();

        assertNotNull(room.gameState(), "A joined room has a game in it");
        assertEquals(RoomStatus.IN_PROGRESS, room.status());
    }

    @Test
    void acceptsACodeTypedInLowerCaseWithSpaces() throws Exception {
        String code = codeFrom(createRoom(hostToken));
        String asTyped = code.substring(0, 3).toLowerCase(java.util.Locale.ROOT)
                + " " + code.substring(3).toLowerCase(java.util.Locale.ROOT);

        join(asTyped, guestToken).andExpect(status().isCreated());
    }

    @Test
    void refusesACodeThatOpensNothing() throws Exception {
        join("BCDFGH", guestToken).andExpect(status().isNotFound());
    }

    @Test
    void refusesACodeThatCouldNotBeOne() throws Exception {
        //Answered without touching the database: the wrong shape is a typo.
        join("AEIOU1", guestToken).andExpect(status().isNotFound());
    }

    @Test
    void refusesToLetSomebodyJoinTheirOwnRoom() throws Exception {
        String code = codeFrom(createRoom(hostToken));

        join(code, hostToken).andExpect(status().isConflict());
    }

    @Test
    void refusesASecondGuest() throws Exception {
        String code = codeFrom(createRoom(hostToken));
        join(code, guestToken).andExpect(status().isCreated());
        String thirdToken = signUpAndIn("gatecrasher", "a-good-password");

        join(code, thirdToken).andExpect(status().isConflict());
    }

    @Test
    void capsHowManyRoomsOneAccountCanHoldOpen() throws Exception {
        //A rate limit bounds how fast rooms appear; only a cap bounds how many
        //exist at once, and it is the total that fills a database.
        for (int room = 0; room < RoomService.MAX_OPEN_ROOMS; room++) {
            createRoom(hostToken).andExpect(status().isCreated());
        }

        createRoom(hostToken).andExpect(status().isTooManyRequests());
    }

    @Test
    void givesAnUnjoinedRoomTheShortestLife() throws Exception {
        //Creating a room and walking away is the cheapest abuse there is: one
        //request holding a code and a row.
        String code = codeFrom(createRoom(hostToken));

        Instant expiry = rooms.findByCode(code).orElseThrow().expiresAt();

        assertTrue(expiry.isBefore(Instant.now().plus(11, ChronoUnit.MINUTES)));
        assertTrue(expiry.isAfter(Instant.now()));
    }

    @Test
    void givesAJoinedGameLongerThanAnUnjoinedRoom() throws Exception {
        String code = codeFrom(createRoom(hostToken));
        Instant beforeJoining = rooms.findByCode(code).orElseThrow().expiresAt();

        join(code, guestToken).andExpect(status().isCreated());

        assertTrue(rooms.findByCode(code).orElseThrow().expiresAt().isAfter(beforeJoining));
    }

    @Test
    void showsARoomOnlyToThePeopleInIt() throws Exception {
        //A stranger is told what somebody with a wrong code is told. Saying
        //"not yours" instead would turn this into a way to find live codes.
        String code = codeFrom(createRoom(hostToken));
        long strangerId = accountId("guest");

        assertTrue(roomService.forPlayer(code, strangerId).isEmpty());
        assertTrue(roomService.forPlayer(code, accountId("host")).isPresent());
    }

    @Test
    void sweepsAwayRoomsWhoseTimeIsUp() throws Exception {
        String code = codeFrom(createRoom(hostToken));
        jdbcTemplate.update(
                "UPDATE game_rooms SET expires_at = ? WHERE code = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), code);

        assertEquals(1, roomService.sweepExpired());
        assertTrue(rooms.findByCode(code).isEmpty());
    }

    @Test
    void leavesLiveRoomsAlone() throws Exception {
        String code = codeFrom(createRoom(hostToken));

        roomService.sweepExpired();

        assertTrue(rooms.findByCode(code).isPresent());
    }

    @Test
    void everyCodeItIssuesIsOneItWouldAccept() throws Exception {
        assertTrue(RoomCode.isValid(codeFrom(createRoom(hostToken))));
    }

    // --- helpers -------------------------------------------------------

    private ResultActions createRoom(String token) throws Exception {
        return mockMvc.perform(post("/api/rooms").header("Authorization", "Bearer " + token));
    }

    private ResultActions join(String code, String token) throws Exception {
        return mockMvc.perform(post("/api/rooms/" + code + "/players")
                .header("Authorization", "Bearer " + token));
    }

    private static String codeFrom(ResultActions created) throws Exception {
        String body = created.andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"code\":\"") + 8;
        return body.substring(start, body.indexOf('"', start));
    }

    private long accountId(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE username_folded = ?", Long.class, username);
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
