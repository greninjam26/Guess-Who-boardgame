/*Author: Gavin Liu, Bagavan Marakathalingasivam, Andreas Li
 * Date: Jan 11 2023
 * Description: this class is used to store the 2 player played the game, the selected character of each player, 
 * the questions they asked and the answers they got. Then it also store the result of the game, 
 * which is who won the game
 * */
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;

public class StoreResult {
	private	PrintWriter write;//the PrintWriter that is writing all the result of the game to the csv file
	public StoreResult() throws Exception {
		write = new PrintWriter(new FileWriter("test.csv", true));//initialize the PrintWriter
	}
	/**
	 * this method will be used to store the data of the game in a pvp game
	 * @param user1 the first player
	 * @param user2 the second player
	 * @param gameResult the username of the player who won the game
	 */
	public void addGameResultPVP(User user1, User user2, String gameResult) {
		storeUser1(user1);
		write.print(user2.getUsername() + "," + user2.getSelectedCharacter().getName());//the username and selected characterof the second user
		write.println(gameResult);//the username of the player that won the game
		write.close();
	}
	/**
	 * this method will be used to store the data of the game in a pvp game
	 * @param user1 the player that is player against the AI
	 * @param gameResult who won the game the AI or the user
	 */
	public void addGameResultPVC(User user1, ComputerPlayer ai, String gameResult) {
		storeUser1(user1);
		write.print("AI," + ai.getSelectedCharacter());//store AI as the second player and it's selected character
		ArrayList<Question> questionsAsked = ai.getQuestionsAsked();
		ArrayList<Boolean> questionsAnswers = ai.getQuestionAnswers(); 
		for (int i = 0; i < questionsAsked.size(); i++) {
			if (questionsAnswers.get(i)) {//when the answer is true
				write.println("," + questionsAsked.get(i) + ", yes");//store the question followed by the answer
			}
			else {//when it is false
				write.println("," + questionsAsked.get(i) + ", no");//store the question followed by the answer
			}
		}
		write.println(gameResult);//the username of the player that won the game
		write.close();
	}
	/**
	 * this method will be used to store data of the user1
	 * @param user1 the first player
	 */
	private void storeUser1(User user1) {
		write.print(user1.getUsername() + "," + user1.getSelectedCharacter().getName());//the username and selected character of the first user
		ArrayList<Question> questionsAsked = user1.getQuestionsAsked();
		ArrayList<Boolean> questionsAnswers = user1.getQuestionAnswers(); 
		for (int i = 0; i < questionsAsked.size(); i++) {
			if (questionsAnswers.get(i)) {//when the answer is true
				write.println("," + questionsAsked.get(i) + ", yes");//store the question followed by the answer
			}
			else {//when it is false
				write.println("," + questionsAsked.get(i) + ", no");//store the question followed by the answer
			}
		}
	}
}
