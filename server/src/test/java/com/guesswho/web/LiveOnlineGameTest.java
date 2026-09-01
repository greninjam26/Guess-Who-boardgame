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
import java.util.List;
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
        //Finished games are recorded now, so they outlive the room and have to
        //be cleared as well or one test counts another's results. Participants
        //and their answers cascade from here.
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
    void aHostWhoOpenedARoomAndWalkedAwayGoesAbsent() {
        //Opening a room counts as arriving — it is a request like any other, and
        //a host who has just created a code is plainly at their machine. What
        //makes them absent is the silence afterwards, not the fact that they
        //have yet to poll.
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        assertTrue(games.state(code, guestToken).join().value().opponentPresent(),
                "Somebody who opened this room moments ago is here");

        jdbcTemplate.update(
                "UPDATE game_rooms SET host_last_seen = ? WHERE code = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), code);

        assertFalse(games.state(code, guestToken).join().value().opponentPresent(),
                "A host who opened a room and closed the app is not waiting there");
    }

    @Test
    void aRejectedMoveStillProvesThePlayerIsThere() {
        //Presence has to survive the rules refusing the move. Somebody pressing
        //a button they are not allowed to press yet is unmistakably sitting
        //there, and recording presence only for moves that succeed would let
        //them be forfeited while actively trying to play.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String waiter = fromHost.yourTurn() ? guestToken : hostToken;
        boolean waiterIsHost = !fromHost.yourTurn();

        jdbcTemplate.update("""
                UPDATE game_rooms SET host_last_seen = ?, guest_last_seen = ?
                WHERE code = ?
                """,
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), code);

        //Out of turn, so the rules turn it down and the transaction rolls back.
        assertEquals(OnlineOutcome.Kind.REFUSED,
                games.ask(code, "Does your character wear glasses?", waiter).join().kind());

        java.sql.Timestamp lastSeen = jdbcTemplate.queryForObject(
                "SELECT " + (waiterIsHost ? "host_last_seen" : "guest_last_seen")
                        + " FROM game_rooms WHERE code = ?", java.sql.Timestamp.class, code);
        assertTrue(lastSeen.toInstant().isAfter(java.time.Instant.now().minusSeconds(30)),
                "A refused move rolled the player's presence back with it");
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

    @Test
    void givesTheGameToWhoeverStayedWhenATurnRunsOut() {
        //The abandoner pays, not the person waiting. Passing the turn instead
        //would only move the stall along and still need the sweep to end it.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String stayed = fromHost.yourTurn() ? guestToken : hostToken;
        String stayedName = fromHost.yourTurn() ? "guest" : "host";

        everybodyWalksAway(code);

        RoomState after = games.state(code, stayed).join().value();
        assertEquals(RoomStatus.FINISHED, after.status());
        assertEquals(stayedName, after.winner());
    }

    @Test
    void leavesTheGameAloneWhileTheSlowPlayerIsStillWatching() {
        //A turn running out is not on its own a reason to end somebody's game.
        //Presence is recorded precisely so that thinking for a long time and
        //closing the laptop stop looking the same, and a player staring at the
        //board must not lose it to a clock they can see running.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String thinking = fromHost.yourTurn() ? hostToken : guestToken;

        everybodyWalksAway(code);

        //Their own poll is proof they are there, even though the stored
        //timestamp is older than the presence window.
        RoomState after = games.state(code, thinking).join().value();
        assertEquals(RoomStatus.IN_PROGRESS, after.status());
    }

    @Test
    void doesNotEndAGameThatHasAlreadyExpired() {
        //Between a room expiring and the sweep reaching it the row is still
        //there. Forfeiting in that window settles a game the server has given
        //up on, and the write carries a deadline that revives the room.
        String code = playingGame();
        everybodyWalksAway(code);
        jdbcTemplate.update("UPDATE game_rooms SET expires_at = ? WHERE code = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)), code);

        OnlineOutcome<RoomState> outcome = games.state(code, hostToken).join();

        assertFalse(outcome.isOk(), "An expired room answered as though it were live");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_rooms WHERE code = ? AND status = ?",
                Integer.class, code, RoomStatus.FINISHED.name()),
                "An expired game was forfeited rather than left to the sweep");
    }

    @Test
    void blamesWhoeverOwedTheMoveRatherThanWhoseTurnItIs() {
        //A question that has been asked is owed an answer by the other player,
        //and it is they who are holding the game up even though the turn still
        //belongs to the asker.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String asker = fromHost.yourTurn() ? hostToken : guestToken;
        String askerName = fromHost.yourTurn() ? "host" : "guest";
        games.ask(code, "Does your character wear glasses?", asker).join();

        everybodyWalksAway(code);

        //The asker did their part; the answerer did not.
        assertEquals(askerName, games.state(code, asker).join().value().winner());
    }

    @Test
    void leavesAGameAloneWhileTheTurnStillHasTimeOnIt() {
        String code = playingGame();

        assertEquals(RoomStatus.IN_PROGRESS,
                games.state(code, hostToken).join().value().status());
    }

    @Test
    void doesNotForfeitAGameNobodyHasJoined() {
        //Nobody owes a move in a room with one player in it, and it has its own
        //shorter expiry for exactly this.
        String code = games.createRoom(hostToken).join().value().code();
        everybodyWalksAway(code);

        assertEquals(RoomStatus.WAITING,
                games.state(code, hostToken).join().value().status());
    }

    @Test
    void forfeitsOnlyOnceWhenTwoPollsArriveTogether() throws Exception {
        //Genuinely at the same time. Two polls one after the other prove
        //nothing: the second finds a finished game and returns early without
        //ever reaching the optimistic update the version check exists for.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String waiting = fromHost.yourTurn() ? guestToken : hostToken;
        everybodyWalksAway(code);
        long before = version(code);

        int polls = 8;
        java.util.concurrent.CountDownLatch ready =
                new java.util.concurrent.CountDownLatch(polls);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(polls);
        try {
            List<java.util.concurrent.Future<OnlineOutcome<RoomState>>> polled =
                    new java.util.ArrayList<>();
            for (int poll = 0; poll < polls; poll++) {
                polled.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return games.state(code, waiting).join();
                }));
            }
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS),
                    "Pollers never got going");
            go.countDown();

            for (java.util.concurrent.Future<OnlineOutcome<RoomState>> poll : polled) {
                OnlineOutcome<RoomState> outcome = poll.get(20,
                        java.util.concurrent.TimeUnit.SECONDS);
                assertTrue(outcome.isOk(), "A simultaneous poll failed outright");
            }
        }
        finally {
            pool.shutdownNow();
        }

        //One forfeit, not eight. The version counts every write that landed, so
        //a second one getting through shows up here — where comparing the two
        //reported winners would not, since every poll returns the same winner
        //whether it forfeited the game or merely read the result.
        assertEquals(before + 1, version(code),
                "Simultaneous polls forfeited the same game more than once");
    }

    /** How many writes have landed on a room. */
    private long version(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM game_rooms WHERE code = ?", Long.class, code);
    }

    /** A joined game with both characters chosen. */
    private String playingGame() {
        String code = games.createRoom(hostToken).join().value().code();
        games.joinRoom(code, guestToken).join();
        games.chooseCharacter(code, "Olivia", hostToken).join();
        games.chooseCharacter(code, "Sam", guestToken).join();
        return code;
    }

    @Test
    void recordsAFinishedOnlineGameAgainstBothPlayersAccounts() {
        //Without this the whole online feature is invisible to the leaderboard
        //the accounts exist for: two people play a real game and neither record
        //changes.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String mover = fromHost.yourTurn() ? hostToken : guestToken;
        String theirOpponentsCharacter = mover.equals(hostToken) ? "Sam" : "Olivia";

        games.guess(code, theirOpponentsCharacter, mover).join();

        assertEquals("PVP_ONLINE", jdbcTemplate.queryForObject(
                "SELECT mode FROM game_results", String.class),
                "An online game filed under the wrong mode is on the wrong board");
        //Both sides are signed in and on their own machines, so the game belongs
        //to both records rather than to whichever client reported it.
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM game_result_participants
                WHERE account_id IS NOT NULL
                """, Integer.class),
                "An online result left a player unattributed");
    }

    @Test
    void recordsAForfeitedGameToo() {
        //The forfeit exists to give the player who stayed a result. A room
        //quietly marked finished is not one.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String stayed = fromHost.yourTurn() ? guestToken : hostToken;
        String stayedName = fromHost.yourTurn() ? "guest" : "host";
        everybodyWalksAway(code);

        games.state(code, stayed).join();

        assertEquals(stayedName, jdbcTemplate.queryForObject(
                "SELECT winner FROM game_results", String.class),
                "A forfeited game left no record for the player who stayed");
    }

    @Test
    void recordsAFinishedGameOnlyOnceHoweverOftenItIsRead() {
        //Both players poll a finished game, and either could be the one to
        //notice. Recording on "the game is over" rather than on "this call is
        //what ended it" would file the same game again on every poll.
        String code = playingGame();
        RoomState fromHost = games.state(code, hostToken).join().value();
        String stayed = fromHost.yourTurn() ? guestToken : hostToken;
        everybodyWalksAway(code);

        for (int poll = 0; poll < 5; poll++) {
            games.state(code, stayed).join();
            games.state(code, hostToken).join();
            games.state(code, guestToken).join();
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM game_results", Integer.class),
                "One game produced more than one result");
    }

    /**
     * Runs the turn out and empties both chairs.
     *
     * <p>Ageing the last move alone is no longer enough to forfeit anything, and
     * that is the point: a turn running out matters only once the player who
     * owes it has also stopped being heard from. Both sightings are aged so that
     * whichever player the test then polls as is the only one who counts as
     * present, by virtue of the poll itself.</p>
     */
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
        AccountClient.Outcome signedIn = accounts.logIn(username, password).join();
        assertTrue(signedIn.isLoggedIn(), "Could not sign in as " + username);
        return signedIn.token();
    }
}
