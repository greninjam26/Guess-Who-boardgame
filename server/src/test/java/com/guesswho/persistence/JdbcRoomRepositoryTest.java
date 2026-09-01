package com.guesswho.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.room.RoomStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = GuessWhoServerApplication.class)
class JdbcRoomRepositoryTest {
    @Autowired
    private RoomRepository rooms;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long accountId;

    @BeforeEach
    void anAccount() {
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                "INSERT INTO accounts (username, username_folded, password_hash)"
                        + " VALUES ('host', 'host', 'x')");
        accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM accounts", Long.class);
    }

    @Test
    void aNewRoomStartsAtVersionZero() {
        assertEquals(0, room().version());
    }

    @Test
    void everyWriteMovesTheVersionOn() {
        RoomRepository.StoredRoom created = room();

        assertTrue(update(created.code(), 0));

        assertEquals(1, rooms.findByCode(created.code()).orElseThrow().version());
    }

    @Test
    void refusesAWriteBasedOnAVersionThatHasMovedOn() {
        //Both players choosing at the same moment: each reads a game where
        //nobody has chosen, each writes its own choice. Without this the second
        //silently replaces the first and one character simply disappears.
        RoomRepository.StoredRoom created = room();

        assertTrue(update(created.code(), 0), "The first write should land");
        assertFalse(update(created.code(), 0), "The second was based on a game that has moved");
    }

    @Test
    void letsTheLoserWriteOnceItHasCaughtUp() {
        //Rejection is not the end of it: the client polls, sees the new state,
        //and the player acts from there.
        RoomRepository.StoredRoom created = room();
        update(created.code(), 0);

        assertTrue(update(created.code(), 1));
    }

    @Test
    void doesNotMoveTheVersionOnForAWriteItRefused() {
        RoomRepository.StoredRoom created = room();
        update(created.code(), 0);

        update(created.code(), 0);

        assertEquals(1, rooms.findByCode(created.code()).orElseThrow().version(),
                "A refused write must not count as one that happened");
    }

    @Test
    void keepsWhatTheWinningWriteStored() {
        RoomRepository.StoredRoom created = room();

        rooms.updateGame(created.code(), "{\"first\":true}", RoomStatus.IN_PROGRESS,
                Instant.now().plus(10, ChronoUnit.MINUTES), 0);
        rooms.updateGame(created.code(), "{\"second\":true}", RoomStatus.IN_PROGRESS,
                Instant.now().plus(10, ChronoUnit.MINUTES), 0);

        assertTrue(rooms.findByCode(created.code()).orElseThrow().gameState()
                .contains("first"), "The rejected write replaced the one that landed");
    }

    private RoomRepository.StoredRoom room() {
        return rooms.create(com.guesswho.room.RoomCode.next(), accountId,
                Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    private boolean update(String code, long expectedVersion) {
        return rooms.updateGame(code, "{}", RoomStatus.IN_PROGRESS,
                Instant.now().plus(10, ChronoUnit.MINUTES), expectedVersion);
    }
}
