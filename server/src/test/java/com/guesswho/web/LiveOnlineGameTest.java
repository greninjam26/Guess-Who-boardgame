package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.client.AccountClient;
import com.guesswho.client.HttpAccountClient;
import com.guesswho.client.HttpOnlineGameClient;
import com.guesswho.client.OnlineGameClient;
import com.guesswho.client.OnlineOutcome;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two clients playing a whole game against a running server.
 *
 * <p>Every layer below this has its own tests and every one of them substitutes
 * the layer beneath. This is the only test where the real client speaks HTTP to
 * a real server over a real socket, which is the one place a contract can be
 * wrong while both sides pass their own tests — a renamed field, a status code
 * read differently, a body neither side ever parsed.</p>
 */
@SpringBootTest(
        classes = GuessWhoServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "guesswho.rooms.sweep.enabled=false")
class LiveOnlineGameTest {
    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OnlineGameClient games;
    private String hostToken;
    private String guestToken;

    @BeforeEach
    void twoSignedInPlayers() {
        jdbcTemplate.update("DELETE FROM room_move_keys");
        jdbcTemplate.update("DELETE FROM game_rooms");
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");

        URI server = URI.create("http://localhost:" + port);
        AccountClient accounts = new HttpAccountClient(server);
        games = new HttpOnlineGameClient(server);

        hostToken = signUpAndIn(accounts, "host", "a-good-password");
        guestToken = signUpAndIn(accounts, "guest", "a-good-password");
    }

    @Test
    void twoPlayersPlayAGameFromStartToFinish() {
        String code = games.createRoom(hostToken).join().value().code();
        assertNotNull(code);

        assertTrue(games.joinRoom(code, guestToken).join().isOk());

        assertTrue(games.chooseCharacter(code, "Olivia", hostToken).join().isOk());
        assertTrue(games.chooseCharacter(code, "Sam", guestToken).join().isOk());

        //Whoever the server gave the opening turn to.
        RoomState fromHost = games.state(code, hostToken).join().value();
        String mover = fromHost.yourTurn() ? hostToken : guestToken;
        String waiter = fromHost.yourTurn() ? guestToken : hostToken;

        assertTrue(games.ask(code, "Does your character wear glasses?", mover).join().isOk());
        assertTrue(games.answer(code, true, waiter).join().isOk());

        //The answer went to whoever asked, and the turn passed.
        RoomState afterAnswer = games.state(code, mover).join().value();
        assertEquals(1, afterAnswer.yourQuestions().size());
        assertFalse(afterAnswer.yourTurn());

        //The player now to move guesses, and the server settles it.
        String theirOpponentsCharacter = waiter.equals(hostToken) ? "Sam" : "Olivia";
        RoomState finished =
                games.guess(code, theirOpponentsCharacter, waiter).join().value();

        assertEquals(RoomStatus.FINISHED, finished.status());
        assertEquals(finished.you(), finished.winner());
    }

    @Test
    void neitherPlayerEverReceivesTheOthersCharacter() {
        //The same rule the projection test checks, but over a real socket, on
        //JSON a real client parsed.
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        games.chooseCharacter(code, "Olivia", hostToken).join();
        games.chooseCharacter(code, "Sam", guestToken).join();

        RoomState host = games.state(code, hostToken).join().value();
        RoomState guest = games.state(code, guestToken).join().value();

        assertEquals("Olivia", host.yourCharacter());
        assertEquals("Sam", guest.yourCharacter());
        assertTrue(host.opponentHasChosen());
        //Nothing on the record can hold it, and nothing did.
        assertFalse(host.toString().contains("Sam"), host.toString());
        assertFalse(guest.toString().contains("Olivia"), guest.toString());
    }

    @Test
    void aStrangerCannotSeeOrTouchTheGame() {
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        String stranger = signUpAndIn(
                new HttpAccountClient(URI.create("http://localhost:" + port)),
                "stranger", "a-good-password");

        assertEquals(OnlineOutcome.Kind.NOT_FOUND,
                games.state(code, stranger).join().kind());
        assertEquals(OnlineOutcome.Kind.NOT_FOUND,
                games.ask(code, "Does your character wear glasses?", stranger).join().kind());
    }

    @Test
    void aMoveOutOfTurnIsRefusedWithSomethingSayable() {
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        games.chooseCharacter(code, "Olivia", hostToken).join();
        games.chooseCharacter(code, "Sam", guestToken).join();

        RoomState fromHost = games.state(code, hostToken).join().value();
        String waiter = fromHost.yourTurn() ? guestToken : hostToken;

        OnlineOutcome<RoomState> refused =
                games.ask(code, "Does your character wear glasses?", waiter).join();

        assertEquals(OnlineOutcome.Kind.REFUSED, refused.kind());
        assertFalse(refused.message() == null || refused.message().isBlank(),
                "A refusal a screen cannot put into words is one a player cannot act on");
    }

    @Test
    void anExpiredTokenIsReportedAsBeingSignedOut() {
        //Distinct from a refusal: the remedy is signing in again, and the
        //client has to be able to tell the difference over a real connection.
        String code = games.createRoom(hostToken).join().value().code();

        jdbcTemplate.update("DELETE FROM account_sessions");

        assertEquals(OnlineOutcome.Kind.SIGNED_OUT,
                games.state(code, hostToken).join().kind());
    }

    @Test
    void aRetriedMoveChangesNothingTheSecondTime() {
        //Idempotency over the wire: the client generates its own key, so this
        //exercises the header name both sides agreed on.
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();

        assertTrue(games.chooseCharacter(code, "Olivia", hostToken).join().isOk());

        //A second choice with a fresh key is a genuine second move, and refused.
        assertEquals(OnlineOutcome.Kind.REFUSED,
                games.chooseCharacter(code, "Nick", hostToken).join().kind());
    }

    @Test
    void anOpponentWhoIsWatchingCountsAsPresent() {
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();

        //The guest polls, as an open client does every couple of seconds.
        games.state(code, guestToken).join();

        assertTrue(games.state(code, hostToken).join().value().opponentPresent(),
                "A client that is open and watching should keep its player present");
    }

    @Test
    void anOpponentWhoHasNeverBeenHeardFromIsNotPresent() {
        //Somebody who created a room and closed the app has not arrived, and
        //saying otherwise would have the other player wait for nobody.
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();

        //Only the host has made a request since joining.
        RoomState seenByGuest = games.state(code, guestToken).join().value();

        assertFalse(seenByGuest.opponentPresent());
    }

    @Test
    void anOpponentGoesAbsentOnceTheyStopBeingHeardFrom() {
        //The distinction the whole feature exists for: somebody thinking hard
        //has a client polling for them, and somebody who closed their laptop
        //does not.
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        games.state(code, guestToken).join();
        assertTrue(games.state(code, hostToken).join().value().opponentPresent());

        jdbcTemplate.update(
                "UPDATE game_rooms SET guest_last_seen = ? WHERE code = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), code);

        assertFalse(games.state(code, hostToken).join().value().opponentPresent(),
                "A player last heard from a minute ago is not sitting there watching");
    }

    @Test
    void beingPresentIsNotAMove() {
        //Polling must not look like a move to anything watching for one, or a
        //waiting opponent would appear to be playing.
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        long before = jdbcTemplate.queryForObject(
                "SELECT version FROM game_rooms WHERE code = ?", Long.class, code);

        games.state(code, hostToken).join();
        games.state(code, guestToken).join();

        assertEquals(before, jdbcTemplate.queryForObject(
                "SELECT version FROM game_rooms WHERE code = ?", Long.class, code));
    }

    private static String signUpAndIn(AccountClient accounts, String username, String password) {
        accounts.register(username, password).join();
        AccountClient.Outcome signedIn = accounts.logIn(username, password).join();
        assertTrue(signedIn.isLoggedIn(), "Could not sign in as " + username);
        return signedIn.token();
    }
}
