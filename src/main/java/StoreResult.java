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
 * Appends completed game histories to a CSV file. Each participant occupies
 * one row followed by a row containing the winner.
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
	 * Stores both human players, their question histories, and the winner of a
	 * player-versus-player game.
	 *
	 * @param user1 the first player
	 * @param user2 the second player
	 * @param gameResult the username of the player who won the game
	 */
	public void addGameResultPVP(User user1, User user2, String gameResult) {
		storePlayer(user1.getUsername(), user1);
		storePlayer(user2.getUsername(), user2);
		writeRow(List.of(gameResult));//the username of the player that won the game
		write.close();
	}
	/**
	 * Stores the human player, computer opponent, their question histories, and
	 * the winner of a player-versus-computer game.
	 *
	 * @param user1 the player competing against the computer
	 * @param ai the computer opponent
	 * @param gameResult the human player's username or {@code AI}
	 */
	public void addGameResultPVC(User user1, ComputerPlayer ai, String gameResult) {
		storePlayer(user1.getUsername(), user1);
		storePlayer("AI", ai);
		writeRow(List.of(gameResult));//the username of the player that won the game
		write.close();
	}
	/**
	 * Stores one participant and all of their question results on a single row.
	 * @param name the participant name written to the result
	 * @param player the participant whose game data is stored
	 */
	private void storePlayer(String name, Player player) {
		ArrayList<String> fields = new ArrayList<>();
		fields.add(name);
		fields.add(player.getSelectedCharacter().getName());
		for (int i = 0; i < player.getQuestionsAsked().size(); i++) {
			fields.add(player.getQuestionsAsked().get(i).getQuestion());
			fields.add(player.getQuestionAnswers().get(i) ? " yes" : " no");
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
