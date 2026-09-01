package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.persistence.RoomRepository;
import java.sql.Timestamp;
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

@SpringBootTest(classes = GuessWhoServerApplication.class,
        properties = "guesswho.rooms.sweep.enabled=false")
@AutoConfigureMockMvc
class RoomSweeperTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RoomService roomService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String host;
    private String guest;

    @BeforeEach
    void twoPlayers() throws Exception {
        jdbcTemplate.update("DELETE FROM room_move_keys");
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");
        host = signUpAndIn("host", "a-good-password");
        guest = signUpAndIn("guest", "a-good-password");
    }

    @Test
    void reachesARoomNobodyWillEverReadAgain() throws Exception {
        //The case a lazy expiry never touches, and the only one that actually
        //accumulates: a room created, never shared, and abandoned.
        String code = createRoom();
        expire(code);

        new RoomSweeper(roomService).sweep();

        assertTrue(rooms.findByCode(code).isEmpty());
    }

    @Test
    void leavesRoomsThatAreStillAlive() throws Exception {
        String code = createRoom();

        new RoomSweeper(roomService).sweep();

        assertTrue(rooms.findByCode(code).isPresent());
    }

    @Test
    void survivesTheDatabaseBeingUnavailable() {
        //A sweep that could bring the server down would be a worse problem
        //than the rows it was tidying.
        RoomSweeper sweeper = new RoomSweeper(new RoomService(null) {
            @Override
            public int sweepExpired() {
                throw new IllegalStateException("database is gone");
            }
        });

        sweeper.sweep();
    }

    @Test
    void doesNotLetAnActiveGameOutliveItsCeiling() throws Exception {
        //Every move pushes the deadline out. Without a ceiling, two people
        //poking at a game keep it alive for ever.
        String code = createRoom();
        mockMvc.perform(post("/api/rooms/" + code + "/players")
                .header("Authorization", "Bearer " + guest));
        //Pretend the room was opened a day ago.
        jdbcTemplate.update("UPDATE game_rooms SET created_at = ? WHERE code = ?",
                Timestamp.from(Instant.now().minus(23, ChronoUnit.HOURS)), code);

        choose(host, "Olivia");

        Instant deadline = rooms.findByCode(code).orElseThrow().expiresAt();
        assertTrue(deadline.isBefore(Instant.now().plus(70, ChronoUnit.MINUTES)),
                "A room opened 23 hours ago cannot be given another 30 minutes "
                        + "beyond its ceiling: " + deadline);
    }

    @Test
    void stillGivesAFreshGameItsFullIdleTime() throws Exception {
        String code = createRoom();
        mockMvc.perform(post("/api/rooms/" + code + "/players")
                .header("Authorization", "Bearer " + guest));

        choose(host, "Olivia");

        Instant deadline = rooms.findByCode(code).orElseThrow().expiresAt();
        assertTrue(deadline.isAfter(Instant.now().plus(25, ChronoUnit.MINUTES)));
    }

    @Test
    void recordsWhenARoomWasOpened() throws Exception {
        assertNotNull(rooms.findByCode(createRoom()).orElseThrow().createdAt(),
                "The ceiling is measured from this, so it cannot be missing");
    }

    @Test
    void removesTheMoveKeysBelongingToASweptRoom() throws Exception {
        String code = createRoom();
        jdbcTemplate.update(
                "INSERT INTO room_move_keys (room_code, move_key) VALUES (?, 'a-key')", code);
        expire(code);

        new RoomSweeper(roomService).sweep();

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_move_keys", Integer.class));
    }

    // --- helpers -------------------------------------------------------

    private void expire(String code) {
        jdbcTemplate.update("UPDATE game_rooms SET expires_at = ? WHERE code = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), code);
    }

    private String createRoom() throws Exception {
        ResultActions created = mockMvc.perform(post("/api/rooms")
                .header("Authorization", "Bearer " + host));
        String body = created.andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"code\":\"") + 8;
        return body.substring(start, body.indexOf('"', start));
    }

    private void choose(String token, String character) throws Exception {
        String code = jdbcTemplate.queryForObject(
                "SELECT code FROM game_rooms", String.class);
        mockMvc.perform(post("/api/rooms/" + code + "/character")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"character\": \"%s\"}".formatted(character)));
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
