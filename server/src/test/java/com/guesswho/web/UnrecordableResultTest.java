package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.client.AccountClient;
import com.guesswho.client.HttpAccountClient;
import com.guesswho.client.HttpOnlineGameClient;
import com.guesswho.client.OnlineGameClient;
import com.guesswho.game.GameResult;
import com.guesswho.persistence.GameResultRepository;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What happens when a finished game cannot be written down.
 *
 * <p>Rooms and results share one database, so a failed result insert means that
 * database is unavailable — and the write that finished the room has therefore
 * not committed either. The two must fail together. A room that finished while
 * its result did not is a game the players are told they have completed and
 * that appears on nobody's record, with nothing left to retry from.</p>
 *
 * <p>Both ways a game can end are covered, because they take different paths to
 * the same write: a guess runs inside the move's transaction, a forfeit inside
 * the poll's.</p>
 */
@SpringBootTest(
        classes = {GuessWhoServerApplication.class, UnrecordableResultTest.FailingResults.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "guesswho.rooms.sweep.enabled=false")
class UnrecordableResultTest {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OnlineGameClient games;
    private String hostToken;
    private String guestToken;

    /**
     * A result store that is always unavailable.
     *
     * <p>Marked primary so it stands in for the JDBC one everywhere the
     * interface is injected, which is how the failure reaches the code under
     * test without a database that has to be broken on purpose.</p>
     */
    @TestConfiguration
    static class FailingResults {
        /**
         * @return a repository that refuses every write
         */
        @Bean
        @Primary
        GameResultRepository unavailableResults() {
            return new GameResultRepository() {
                @Override
                public void save(GameResult gameResult) {
                    throw new DataAccessResourceFailureException("results are unavailable");
                }

                @Override
                public void save(GameResult gameResult, Long accountId) {
                    throw new DataAccessResourceFailureException("results are unavailable");
                }

                @Override
                public void saveOwnedBy(
                        GameResult gameResult, Map<String, Long> accountsByParticipantName) {
                    throw new DataAccessResourceFailureException("results are unavailable");
                }
            };
        }
    }

    @BeforeEach
    void twoSignedInPlayers() {
        jdbcTemplate.update("DELETE FROM room_move_keys");
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM game_results");
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");

        URI server = URI.create("http://localhost:" + port);
        AccountClient accounts = new HttpAccountClient(server);
        games = new HttpOnlineGameClient(server);
        hostToken = signUpAndIn(accounts, "host", "a-good-password");
        guestToken = signUpAndIn(accounts, "guest", "a-good-password");
    }

    @Test
    void leavesAGuessedGameUnfinishedWhenItsResultCannotBeStored() {
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String mover = fromHost.yourTurn() ? hostToken : guestToken;
        String theirCharacter = mover.equals(hostToken) ? "Sam" : "Olivia";

        //The guess is right, so the game would finish if the result could be
        //written. It cannot, so nothing happens at all.
        assertFalse(games.guess(code, theirCharacter, mover).join().isOk(),
                "A guess whose result cannot be stored should not report success");

        assertEquals(RoomStatus.IN_PROGRESS.name(), jdbcTemplate.queryForObject(
                "SELECT status FROM game_rooms WHERE code = ?", String.class, code),
                "The room finished without its result being recorded");
    }

    @Test
    void leavesTheMoveKeyFreeSoTheGuessCanBeRetried() {
        //The point of failing rather than swallowing: the move is still there
        //to be made. This checks the precondition for that — the key rolled back
        //with the rest, so the client's ordinary retry is not mistaken for a
        //duplicate. It does not check the retry itself, which needs a store that
        //starts working again.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String mover = fromHost.yourTurn() ? hostToken : guestToken;
        String theirCharacter = mover.equals(hostToken) ? "Sam" : "Olivia";
        //Both character choices claimed a key of their own, and those landed.
        //It is only the guess's key that must not survive its rollback.
        int beforeTheGuess = keysClaimed();

        games.guess(code, theirCharacter, mover).join();

        assertEquals(beforeTheGuess, keysClaimed(),
                "A move that was rolled back consumed its key anyway, so no retry can work");
    }

    private int keysClaimed() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_move_keys", Integer.class);
    }

    @Test
    void leavesAForfeitUnappliedWhenItsResultCannotBeStored() {
        //The forfeit path reaches the same write from a poll rather than a move.
        //Swallowing here would finish the room and lose the record with nothing
        //left to retry from — the room would never be in progress again.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String stayed = fromHost.yourTurn() ? guestToken : hostToken;
        everybodyWalksAway(code);

        games.state(code, stayed).join();

        assertEquals(RoomStatus.IN_PROGRESS.name(), jdbcTemplate.queryForObject(
                "SELECT status FROM game_rooms WHERE code = ?", String.class, code),
                "A forfeit finished the room while its result was lost");
    }

    /** A joined game with both characters chosen. */
    private String playingGame() {
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        games.chooseCharacter(code, "Olivia", hostToken).join();
        games.chooseCharacter(code, "Sam", guestToken).join();
        return code;
    }

    private void everybodyWalksAway(String code) {
        java.sql.Timestamp longAgo = java.sql.Timestamp.from(java.time.Instant.now()
                .minus(RoomService.TURN_LIMIT).minusSeconds(60));
        jdbcTemplate.update("""
                UPDATE game_rooms
                SET updated_at = ?, host_last_seen = ?, guest_last_seen = ?
                WHERE code = ?
                """, longAgo, longAgo, longAgo, code);
    }

    private static String signUpAndIn(AccountClient accounts, String username, String password) {
        accounts.register(username, password).join();
        return accounts.logIn(username, password).join().token();
    }
}
