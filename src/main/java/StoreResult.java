/*Author: Gavin Liu
 * Date: Jan 11 2023
 * Description: this class is used to store the 2 player played the game, the selected character of each player, 
 * the questions they asked and the answers they got. Then it also store the result of the game, 
 * which is who won the game
 * */
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends completed game-result snapshots to a CSV file. Each participant
 * occupies one row followed by a row containing the winner.
 */
public class StoreResult {
	private final PrintWriter write;//the PrintWriter that is writing all the result of the game to the csv file
	/**
	 * Creates a result writer that appends to {@code test.csv}.
	 *
	 * @throws Exception if the result file cannot be opened
	 */
	public StoreResult() throws Exception {
		this(new FileWriter("test.csv", true));
	}
	StoreResult(Writer writer) {
		write = new PrintWriter(writer);
	}
	/**
	 * Stores every participant and the winner from a completed game snapshot.
	 *
	 * @param gameResult immutable completed-game data
	 */
	public void addGameResult(GameResult gameResult) {
		for (GameResult.Participant participant : gameResult.participants()) {
			storeParticipant(participant);
		}
		writeRow(List.of(gameResult.winner()));
		write.close();
	}
	private void storeParticipant(GameResult.Participant participant) {
		ArrayList<String> fields = new ArrayList<>();
		fields.add(participant.name());
		fields.add(participant.selectedCharacter());
		for (GameResult.QuestionAnswer questionAnswer : participant.questionAnswers()) {
			fields.add(questionAnswer.question());
			fields.add(questionAnswer.answer() ? " yes" : " no");
		}
		writeRow(fields);
	}

	private void writeRow(List<String> fields) {
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				write.print(",");
			}
			write.print(escapeCsv(fields.get(i)));
		}
		write.println();
	}

	private String escapeCsv(String value) {
		if (value.contains(",") || value.contains("\"")
				|| value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}
