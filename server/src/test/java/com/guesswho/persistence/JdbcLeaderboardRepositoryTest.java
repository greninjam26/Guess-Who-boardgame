package com.guesswho.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
