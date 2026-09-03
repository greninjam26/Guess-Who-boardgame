import com.guesswho.client.AccountClient;
import com.guesswho.client.FilePendingGameResultStore;
import com.guesswho.client.GameResultSubmissionService;
import com.guesswho.client.HttpAccountClient;
import com.guesswho.client.HttpGameResultClient;
import com.guesswho.client.HttpOnlineGameClient;
import com.guesswho.client.OnlineOutcome;
import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.game.QuestionMode;
import com.guesswho.room.RoomState;
import com.guesswho.room.RoomStatus;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The two-client acceptance rehearsal, driven headlessly against a real server
 * process.
 *
 * <p>Everything here that can be tested in the suite already is: LiveOnlineGameTest
 * plays a whole game over a real socket, PresenceTest owns the clocks, and
 * GameResultSubmissionServiceTest owns the queue. What none of them can do is
 * stop the server. This program owns the server as a child process, so the
 * restart in the middle of a game is a real one — the JVM exits, the pool goes,
 * the row stays.</p>
 */
public class TwoClientRehearsal {
    private static int failures = 0;
    private static int checks = 0;
    private static Process server;
    private static String jar;
    private static String jdbcUrl;
    private static String base;
    private static int port;
    private static Path log;
    private static String dbUser = "sa";
    private static String dbPassword = "";
    private static String mode = "full";

    public static void main(String[] args) throws Exception {
        jar = args[0];
        jdbcUrl = args[1];
        port = Integer.parseInt(args[2]);
        log = Path.of(args[3]);
        base = "http://127.0.0.1:" + port;
        Path queueFile = Path.of(args[4]);
        if (args.length > 5) {
            dbUser = args[5];
        }
        if (args.length > 6) {
            dbPassword = args[6];
        }
        if (args.length > 7) {
            mode = args[7];
        }

        try {
            startServer("first start");

            HttpAccountClient accounts = new HttpAccountClient(URI.create(base));
            HttpOnlineGameClient games = new HttpOnlineGameClient(URI.create(base));
            String suffix = String.valueOf(System.currentTimeMillis() % 100000L);
            final String hostName = "rehearsalhost" + suffix;
            final String guestName = "rehearsalguest" + suffix;

            step("1. Two accounts, created and signed in");
            String hostToken = signUpAndIn(accounts, hostName);
            String guestToken = signUpAndIn(accounts, guestName);
            check(hostToken != null && guestToken != null, "both players hold a session token");
            check(!hostToken.equals(guestToken), "the two sessions are genuinely different");

            step("2. A room, created and joined");
            var created = games.createRoom(hostToken).join();
            check(created.isOk(), "the host opened a room");
            String code = created.value().code();
            say("room code: " + code);
            check(games.joinRoom(code, guestToken).join().isOk(), "the guest joined with the code");

            step("3. Characters chosen, a question asked and answered");
            check(games.chooseCharacter(code, "Olivia", hostToken).join().isOk(),
                    "the host committed to a character");
            check(games.chooseCharacter(code, "Sam", guestToken).join().isOk(),
                    "the guest committed to a character");
            RoomState seenByHost = games.state(code, hostToken).join().value();
            check(seenByHost.opponentHasChosen(), "each side is told the other has chosen");
            check("Olivia".equals(seenByHost.yourCharacter()), "the host sees its own character");
            check(!seenByHost.toString().contains("Sam"),
                    "and nothing in the host's view carries the guest's character");

            String mover = seenByHost.yourTurn() ? hostToken : guestToken;
            String waiter = seenByHost.yourTurn() ? guestToken : hostToken;
            check(games.ask(code, "Does your character wear glasses?", mover).join().isOk(),
                    "the player to move asked a question");
            check(games.answer(code, true, waiter).join().isOk(), "the other player answered");
            check(games.state(code, mover).join().value().yourQuestions().size() == 1,
                    "the answer reached the asker's transcript");

            if ("populate".equals(mode)) {
                step("P. A second game, played to a finish, so the backup has results in it");
                var extra = games.createRoom(hostToken).join();
                String finishedCode = extra.value().code();
                games.joinRoom(finishedCode, guestToken).join();
                games.chooseCharacter(finishedCode, "Olivia", hostToken).join();
                games.chooseCharacter(finishedCode, "Sam", guestToken).join();
                RoomState who = games.state(finishedCode, hostToken).join().value();
                String winner = who.yourTurn() ? hostToken : guestToken;
                games.guess(finishedCode, winner.equals(hostToken) ? "Sam" : "Olivia", winner)
                        .join();
                check(games.state(finishedCode, hostToken).join().value().status()
                                == RoomStatus.FINISHED,
                        "a finished game exists to be backed up");

                Row live = readRoom(code);
                say("LIVE ROOM " + code + " " + live);
                say("accounts: " + hostName + ", " + guestName);
                say("game_results rows: " + countGameResults());
                check(live.status.equals("IN_PROGRESS"),
                        "and a game is still in progress when the backup is taken");
                System.out.println();
                System.out.println(failures == 0
                        ? "POPULATED: " + checks + " checks, live room " + code
                        : "POPULATE FAILED: " + failures + " of " + checks);
                stopServer();
                System.exit(failures == 0 ? 0 : 1);
            }

            step("4. The room as the database holds it, before the restart");
            Row before = readRoom(code);
            say(before.toString());
            check(before.version > 0, "the room has been written to and carries a version");

            //Stated rather than assumed: the restart below is only interesting
            //if it lands on a game that is still being played. A room already
            //finished, or one about to be forfeited, would survive a restart
            //for reasons that have nothing to do with the claim being tested.
            RoomState liveBeforeStop = games.state(code, hostToken).join().value();
            check(liveBeforeStop.status() == RoomStatus.IN_PROGRESS
                            && liveBeforeStop.winner() == null,
                    "the game is live and unwon at the moment the server is stopped");
            check(liveBeforeStop.opponentPresent(),
                    "both players are being heard from, so nothing is near forfeiting");

            step("5. The server stops in the middle of the game");
            stopServer();
            Throwable whileDown = null;
            try {
                games.state(code, hostToken).join();
            }
            catch (java.util.concurrent.CompletionException transportFailure) {
                whileDown = transportFailure.getCause();
            }
            //The client maps HTTP statuses to outcomes; a connection that never
            //reached a status completes exceptionally instead, and RoomPoller
            //is the layer that turns that into UNREACHABLE and the reconnecting
            //banner (RoomPoller.java:110, RoomPollerTest
            //"treatsAThrownFailureAsAnUnreachableServer").
            check(whileDown instanceof java.io.IOException,
                    "a poll against a stopped server fails as a transport error, not as a lost game");
            say("failure while down: " + (whileDown == null ? "none" : whileDown.getClass().getName()));

            step("6. The server starts again and both clients recover on their own");
            startServer("restart");
            OnlineOutcome<RoomState> hostBack = games.state(code, hostToken).join();
            OnlineOutcome<RoomState> guestBack = games.state(code, guestToken).join();
            check(hostBack.isOk() && guestBack.isOk(),
                    "both clients are answered again without signing in or restarting");
            check(hostBack.value().status() == RoomStatus.IN_PROGRESS,
                    "the game is still in progress");
            check(hostBack.value().yourQuestions().size()
                            + guestBack.value().yourQuestions().size() == 1,
                    "the question asked before the restart is still on the record");
            check("Olivia".equals(hostBack.value().yourCharacter()),
                    "the host's committed character survived");
            check(hostBack.value().winner() == null && guestBack.value().winner() == null,
                    "and the restart forfeited the game to nobody");

            step("7. The restart wrote nothing");
            Row after = readRoom(code);
            say(after.toString());
            check(after.version == before.version,
                    "the row's version is unchanged across the restart (" + before.version
                            + " -> " + after.version + ")");
            check(after.stateLength == before.stateLength,
                    "the stored game state is the same size (" + before.stateLength
                            + " -> " + after.stateLength + ")");
            check(after.status.equals(before.status), "the room's status is unchanged");

            step("8. The game plays to a finish after the restart");
            RoomState now = games.state(code, hostToken).join().value();
            String guesser = now.yourTurn() ? hostToken : guestToken;
            String guessed = guesser.equals(hostToken) ? "Sam" : "Olivia";
            OnlineOutcome<RoomState> finished = games.guess(code, guessed, guesser).join();
            check(finished.isOk(), "the player to move could still move");
            check(finished.value().status() == RoomStatus.FINISHED, "the game finished");
            check(finished.value().you().equals(finished.value().winner()),
                    "the correct guess won it");
            check(games.reveal(code, hostToken).join().isOk(),
                    "the ending is reviewable from the host's side");
            check(games.reveal(code, guestToken).join().isOk(),
                    "and from the guest's side");
            check(countGameResults() == 1, "the finished game was recorded once");

            step("9. An opponent who stops being heard from goes absent, without forfeiting");
            var second = games.createRoom(hostToken).join();
            String code2 = second.value().code();
            games.joinRoom(code2, guestToken).join();
            games.chooseCharacter(code2, "Olivia", hostToken).join();
            games.chooseCharacter(code2, "Sam", guestToken).join();
            age(code2, Duration.ofSeconds(30), false);
            RoomState guestView = games.state(code2, guestToken).join().value();
            check(!guestView.opponentPresent(),
                    "30 seconds of silence shows the opponent as gone (present window is 15s)");
            check(guestView.status() == RoomStatus.IN_PROGRESS,
                    "and the game is not taken from them for it");

            step("10. Sustained silence forfeits to whoever stayed");
            RoomState owed = games.state(code2, hostToken).join().value();
            if (!owed.yourTurn()) {
                //Hand the move to the host: the guest asks, the host answers,
                //and the turn is then the host's to take.
                games.ask(code2, "Does your character wear a hat?", guestToken).join();
                games.answer(code2, true, hostToken).join();
                owed = games.state(code2, hostToken).join().value();
            }
            //A hard precondition, not a check. Ageing the silence of a player
            //who does not owe the move proves nothing about forfeiting, and a
            //soft assertion here would let the rest of the step report a pass
            //for the wrong reason.
            if (!owed.yourTurn()) {
                throw new IllegalStateException(
                        "the forfeit case needs the host to owe the move, and it does not");
            }
            check(owed.yourTurn(),
                    "the player who is about to fall silent is the one who owes the move");
            say("ageing host_last_seen and updated_at by 5 minutes for the host, who owes the move");
            age(code2, Duration.ofMinutes(5), true);
            RoomState afterForfeit = games.state(code2, guestToken).join().value();
            check(afterForfeit.status() == RoomStatus.FINISHED,
                    "a turn run out plus sustained silence ends the game");
            check(afterForfeit.you().equals(afterForfeit.winner()),
                    "it goes to the player who was still watching");
            check(countGameResults() == 2, "the forfeited game was recorded too");

            step("11. Offline result queue: server down, game finished, server up");
            Files.deleteIfExists(queueFile);
            GameResultSubmissionService submissions = new GameResultSubmissionService(
                    new HttpGameResultClient(URI.create(base)),
                    new FilePendingGameResultStore(queueFile));
            int recorded = countGameResults();
            stopServer();
            submissions.submit(pveResult(hostName), hostToken).join();
            check(Files.exists(queueFile) && Files.readAllLines(queueFile).size() == 1,
                    "a result completed while the server is down is queued to disk");
            say("queue file: " + queueFile + " (" + Files.size(queueFile) + " bytes)");
            startServer("second restart");
            submissions.submit(pveResult(hostName), hostToken).join();
            List<String> leftOver = Files.exists(queueFile) ? Files.readAllLines(queueFile) : List.of();
            check(leftOver.isEmpty(), "the next successful submission empties the queue");
            check(countGameResults() == recorded + 2,
                    "both the queued result and the current one reached the server");

            step("12. A client too old for the server is told to update");
            HttpClient raw = HttpClient.newHttpClient();
            HttpResponse<String> outdated = raw.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/status"))
                            .header("X-Api-Version", "-1").build(),
                    HttpResponse.BodyHandlers.ofString());
            check(outdated.statusCode() == 426,
                    "a client below the minimum is answered 426, not 400 or 200");
            check(outdated.body().contains("too old"),
                    "the body says what the player should do about it");
            say("426 body: " + outdated.body().trim());
            HttpResponse<String> current = raw.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/status")).build(),
                    HttpResponse.BodyHandlers.ofString());
            check(current.statusCode() == 200, "a client sending no version is still served");
            check("1".equals(current.headers().firstValue("X-Api-Version").orElse(null)),
                    "every response tells the client what the server speaks");
        }
        finally {
            stopServer();
        }

        System.out.println();
        System.out.println(failures == 0
                ? "REHEARSAL PASSED: " + checks + " checks"
                : "REHEARSAL FAILED: " + failures + " of " + checks + " checks");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static String signUpAndIn(AccountClient accounts, String username) {
        accounts.register(username, "a-good-password").join();
        AccountClient.Outcome signedIn = accounts.logIn(username, "a-good-password").join();
        if (!signedIn.isLoggedIn()) {
            throw new IllegalStateException("could not sign in as " + username);
        }
        return signedIn.token();
    }

    private static GameResult pveResult(String winner) {
        return new GameResult(
                List.of(new GameResult.Participant(winner, "Olivia", List.of(), null),
                        new GameResult.Participant("AI", "Nick", List.of(), null)),
                winner, GameMode.PVE, ComputerDifficulty.EASY, QuestionMode.PRESET);
    }

    /** Ages a room so the clocks the server reads have already run out. */
    private static void age(String code, Duration by, boolean alsoTheTurn) throws Exception {
        java.sql.Timestamp then =
                java.sql.Timestamp.from(java.time.Instant.now().minus(by));
        String sql = alsoTheTurn
                ? "UPDATE game_rooms SET updated_at = ?, host_last_seen = ? WHERE code = ?"
                : "UPDATE game_rooms SET host_last_seen = ? WHERE code = ?";
        try (Connection db = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
                PreparedStatement update = db.prepareStatement(sql)) {
            int i = 1;
            update.setTimestamp(i++, then);
            if (alsoTheTurn) {
                update.setTimestamp(i++, then);
            }
            update.setString(i, code);
            update.executeUpdate();
        }
    }

    private record Row(String status, long version, long stateLength) {
        @Override
        public String toString() {
            return "status=" + status + " version=" + version + " game_state length=" + stateLength;
        }
    }

    private static Row readRoom(String code) throws Exception {
        try (Connection db = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
                PreparedStatement query = db.prepareStatement(
                        "SELECT status, version, LENGTH(game_state) FROM game_rooms WHERE code = ?")) {
            query.setString(1, code);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalStateException("no room row for " + code);
                }
                return new Row(row.getString(1), row.getLong(2), row.getLong(3));
            }
        }
    }

    private static int countGameResults() throws Exception {
        try (Connection db = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
                PreparedStatement query =
                        db.prepareStatement("SELECT COUNT(*) FROM game_results")) {
            try (ResultSet row = query.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    private static void startServer(String why) throws Exception {
        ProcessBuilder start = new ProcessBuilder("java", "-jar", jar,
                "--server.port=" + port,
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + dbUser,
                "--spring.datasource.password=" + dbPassword);
        start.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        start.redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()));
        server = start.start();
        long deadline = System.currentTimeMillis() + 90_000;
        HttpClient http = HttpClient.newHttpClient();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse<String> status = http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/status"))
                                .timeout(Duration.ofSeconds(2)).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (status.statusCode() == 200) {
                    say("server up (" + why + ", pid " + server.pid() + "): " + status.body());
                    return;
                }
            }
            catch (Exception notYet) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("server did not come up; see " + log);
    }

    private static void stopServer() throws Exception {
        if (server == null || !server.isAlive()) {
            return;
        }
        long pid = server.pid();
        server.destroy();
        if (!server.waitFor(30, TimeUnit.SECONDS)) {
            server.destroyForcibly();
            server.waitFor(10, TimeUnit.SECONDS);
        }
        say("server stopped (pid " + pid + ")");
    }

    private static void step(String heading) {
        System.out.println();
        System.out.println("== " + heading);
    }

    private static void say(String detail) {
        System.out.println("   . " + detail);
    }

    private static void check(boolean ok, String description) {
        checks++;
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "   PASS " : "   FAIL ") + description);
    }
}
