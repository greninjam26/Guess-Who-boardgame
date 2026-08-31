package com.guesswho.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.game.ComputerDifficulty;
import com.guesswho.game.GameMode;
import com.guesswho.game.GameResult;
import com.guesswho.game.QuestionMode;
import com.guesswho.leaderboard.LeaderboardEntry;
import com.guesswho.leaderboard.LeaderboardRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = GuessWhoServerApplication.class)
class JdbcLeaderboardRepositoryTest {
    @Autowired
    private GameResultRepository gameResultRepository;

    @Autowired
    private LeaderboardRepository leaderboardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearResults() {
        jdbcTemplate.update("DELETE FROM game_results");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void ranksTheBetterRecordFirstWhenWinsAreEqual() {
        //Two wins each, but one of them took ten games to get there.
        winGames("Zoe", 2);
        winGames("Aaron", 2);
        loseGames("Aaron", 8);

        List<LeaderboardEntry> standings = leaderboardRepository.findStandings(null, 10);

        assertEquals(List.of("Zoe", "Aaron"), names(standings),
                "Equal wins should be separated by how many games it took, "
                        + "not by whose name comes first in the alphabet");
    }

    @Test
    void doesNotLetAnAlphabeticalNameOutrankABetterRecord() {
        //The bug this test exists for: sorting the loser's name first because
        //it begins with an A means the leaderboard rewards a username.
        winGames("Aaron", 1);
        winGames("Zoe", 5);

        assertEquals(List.of("Zoe", "Aaron"), names(leaderboardRepository.findStandings(null, 10)));
    }

    @Test
    void ordersByWinsBeforeAnythingElse() {
        winGames("Zoe", 1);
        winGames("Aaron", 3);

        assertEquals(List.of("Aaron", "Zoe"), names(leaderboardRepository.findStandings(null, 10)),
                "More wins comes first, whatever the games played");
    }

    @Test
    void fallsBackToTheNameOnlyWhenRecordsAreIdentical() {
        //Something has to break the tie, or the order changes between calls and
        //a paginated leaderboard can show the same player twice.
        winGames("Zoe", 2);
        winGames("Aaron", 2);

        assertEquals(List.of("Aaron", "Zoe"), names(leaderboardRepository.findStandings(null, 10)));
    }

    @Test
    void countsGamesPlayedAlongsideWins() {
        winGames("Zoe", 2);
        loseGames("Zoe", 3);

        //Not the first row: the computer won the games she lost, so it is on
        //the board too and ahead of her.
        LeaderboardEntry zoe = standingFor("Zoe");

        assertEquals(5, zoe.gamesPlayed());
        assertEquals(2, zoe.wins());
    }

    // --- helpers -------------------------------------------------------

    private void winGames(String name, int count) {
        for (int game = 0; game < count; game++) {
            gameResultRepository.save(result(name, name));
        }
    }

    private void loseGames(String name, int count) {
        for (int game = 0; game < count; game++) {
            gameResultRepository.save(result(name, "AI"));
        }
    }

    private static GameResult result(String player, String winner) {
        return new GameResult(
                List.of(
                        new GameResult.Participant(player, "Olivia", List.of(), null),
                        new GameResult.Participant("AI", "Sam", List.of(), null)),
                winner,
                GameMode.PVE,
                ComputerDifficulty.HARD,
                QuestionMode.PRESET);
    }

    private LeaderboardEntry standingFor(String name) {
        return leaderboardRepository.findStandings(null, 10).stream()
                .filter(entry -> entry.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " is not on the leaderboard"));
    }

    private static List<String> names(List<LeaderboardEntry> standings) {
        return standings.stream()
                .map(LeaderboardEntry::name)
                .filter(name -> !"AI".equals(name))
                .toList();
    }

    // --- accounts ------------------------------------------------------

    @Test
    void keepsASignedInPlayerApartFromAGuestWhoTypedTheSameName() {
        //The whole point of signing in: a row that cannot be joined by
        //somebody typing your name.
        long accountId = account("greninja");
        winGamesAs("greninja", accountId, 3);
        winGames("greninja", 1);

        List<LeaderboardEntry> standings = leaderboardRepository.findStandings(null, 10);

        assertEquals(2, standings.stream()
                .filter(entry -> entry.name().equals("greninja")).count(),
                "The account and the guest are two different players");
        assertTrue(standings.stream()
                .anyMatch(entry -> entry.name().equals("greninja") && entry.registered()));
        assertTrue(standings.stream()
                .anyMatch(entry -> entry.name().equals("greninja") && !entry.registered()));
    }

    @Test
    void addsUpEveryGameAnAccountPlayed() {
        long accountId = account("greninja");
        winGamesAs("greninja", accountId, 2);
        loseGamesAs("greninja", accountId, 1);

        LeaderboardEntry entry = standingFor("greninja");

        assertEquals(3, entry.gamesPlayed());
        assertEquals(2, entry.wins());
        assertTrue(entry.registered());
    }

    @Test
    void showsTheAccountsNameEvenAfterItIsTypedDifferently() {
        //Attribution is by account, so the row is labelled by the account.
        long accountId = account("Greninja");
        gameResultRepository.save(result("greninja", "greninja"), accountId);

        assertTrue(leaderboardRepository.findStandings(null, 10).stream()
                .anyMatch(entry -> entry.name().equals("Greninja") && entry.registered()));
    }

    @Test
    void leavesGamesFromBeforeAccountsUnattributed() {
        //Guessing which account an old row belonged to would put somebody
        //else's games on somebody's record.
        winGames("Player 1", 2);

        LeaderboardEntry entry = standingFor("Player 1");

        assertEquals(2, entry.gamesPlayed());
        assertFalse(entry.registered());
    }

    private long account(String username) {
        jdbcTemplate.update(
                "INSERT INTO accounts (username, username_folded, password_hash)"
                        + " VALUES (?, ?, 'x')",
                username, username.toLowerCase(java.util.Locale.ROOT));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM accounts WHERE username_folded = ?",
                Long.class, username.toLowerCase(java.util.Locale.ROOT));
    }

    private void winGamesAs(String name, long accountId, int count) {
        for (int game = 0; game < count; game++) {
            gameResultRepository.save(result(name, name), accountId);
        }
    }

    private void loseGamesAs(String name, long accountId, int count) {
        for (int game = 0; game < count; game++) {
            gameResultRepository.save(result(name, "AI"), accountId);
        }
    }
}
