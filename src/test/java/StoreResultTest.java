import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class StoreResultTest {
    @Test
    void storesReadableComputerGameValues() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        User user = new User("", 0, "Player");
        user.setSelectedCharacter(user.findCharacter("Olivia"));
        user.recordQuestionAnswer("Is your character's eye colour brown?", false);
        ComputerPlayer computer = new ComputerPlayer("easy", "");
        computer.setSelectedCharacter(computer.findCharacter("Nick"));
        computer.setQuestionAsked("Is your character's eye colour blue?");
        computer.addQuestionAnswers(true);

        storeResult.addGameResultPVC(user, computer, "Player");

        String expected = String.join(System.lineSeparator(),
                "Player,Olivia,Is your character's eye colour brown?, no",
                "AI,Nick,Is your character's eye colour blue?, yes",
                "Player",
                "");
        assertEquals(expected, output.toString());
    }

    @Test
    void storesBothPlayersQuestionsAndWinnerOnSeparateRows() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        User firstPlayer = player("Player 1", "Olivia");
        firstPlayer.recordQuestionAnswer("Is your character's eye colour brown?", false);
        firstPlayer.recordQuestionAnswer("Does your character look friendly?", true);
        User secondPlayer = player("Player 2", "Nick");
        secondPlayer.recordQuestionAnswer("Is your character's eye colour blue?", true);

        storeResult.addGameResultPVP(firstPlayer, secondPlayer, "Player 2");

        String expected = String.join(System.lineSeparator(),
                "Player 1,Olivia,Is your character's eye colour brown?, no,"
                        + "Does your character look friendly?, yes",
                "Player 2,Nick,Is your character's eye colour blue?, yes",
                "Player 2",
                "");
        assertEquals(expected, output.toString());
    }

    @Test
    void keepsRowsSeparateWhenNoQuestionsWereAsked() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        User user = player("Player", "Olivia");
        ComputerPlayer computer = new ComputerPlayer("easy", "");
        computer.setSelectedCharacter(computer.findCharacter("Nick"));

        storeResult.addGameResultPVC(user, computer, "AI");

        String expected = String.join(System.lineSeparator(),
                "Player,Olivia",
                "AI,Nick",
                "AI",
                "");
        assertEquals(expected, output.toString());
    }

    @Test
    void escapesCsvFieldsContainingCommasAndQuotes() throws Exception {
        StringWriter output = new StringWriter();
        StoreResult storeResult = new StoreResult(output);
        User firstPlayer = player("Doe, \"Jane\"", "Olivia");
        User secondPlayer = player("Opponent", "Nick");

        storeResult.addGameResultPVP(firstPlayer, secondPlayer, "Doe, \"Jane\"");

        String expected = String.join(System.lineSeparator(),
                "\"Doe, \"\"Jane\"\"\",Olivia",
                "Opponent,Nick",
                "\"Doe, \"\"Jane\"\"\"",
                "");
        assertEquals(expected, output.toString());
    }

    private User player(String username, String characterName) throws Exception {
        User player = new User("", 0, username);
        player.setSelectedCharacter(player.findCharacter(characterName));
        return player;
    }
}
