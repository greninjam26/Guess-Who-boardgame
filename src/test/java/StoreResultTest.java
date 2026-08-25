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
        user.setQuestionAsked("Is your character's eye colour brown?");
        user.addQuestionAnswers(false);
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
}
