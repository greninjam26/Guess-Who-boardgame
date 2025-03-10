/*Author: Gavin Liu
 * Date: Jan 8 2024
 * Description: this class used to have all the method that is needed to start he 4 different mode of the game
 * and be able to let player ask questions and make guesses to both AI opponent and another player opponent
 * */
import java.util.Random;

public class Game {
	//creating all the variables
	private String state;//the state of the game
	private String gameResult;//the result of the game, which is the username of the player who won the game
	private String username1;//username of the first player
	private int birthday1;//birthday of the first player
	private User user1;//the User object of the first player
	private String username2;//username of the second player
	private int birthday2;//birthday of the second player
	private User user2;//the User oject of the second player
	private ComputerPlayer AI;//the ComputerPlayer object which is the AI
	public Game() {
		//initialize
		state = "Starting";
		gameResult = "";
	}
	/**
	 * this method will return the state of the game
	 * @return the state of the game
	 */
	public String getState() {
		return state;
	}
	/**
	 * this method will return the first user playing in the game
	 * @return the first player
	 */
	public User getUser1() {
		return user1;
	}
	/**
	 * this method will return the second user playing in the game
	 * @return the second player
	 */
	public User getUser2() {
		return user2;
	}
	/**
	 * this method will return the AI that the user is playing
	 * @return the AI that is playing
	 */
	public ComputerPlayer getAI() {
		return AI;
	}
	/**
	 * set the state of the to newState
	 * @param newState the new state of the game
	 */
	public void setState(String newState) {
		state = newState;
	}
	/**
	 * this method choose the player or the AI who goes first randomly, change the state and create user1 and AI
	 * @param newState the new state for the game
	 * @param newUsername the username of the palyer
	 * @return this method will return a String with the text of who is going first. 
	 * @throws Exception
	 */
	public String playerVsComputerRandom(String newState, String newUsername) throws Exception {
		state = newState;//set the state to newState
		Random rand = new Random();
		username1 = newUsername;
		user1 = new User("", 0, username1);//creating the user1 with the inputed username
		AI = new ComputerPlayer(state.substring(3), "");
		int choice = rand.nextInt(2);//randomly pick a number between 1 and 2
		if (choice == 1) {//if it is 1 player going first
			user1.setIsTurn(true);
			AI.setIsTurn(false);
			return "You are going first"; 
		}
		else {//it is 2 AI go first
			user1.setIsTurn(false);
			AI.setIsTurn(true);
			return "The AI is going first";
		}
	}
	/**
	 * this method is used for when the user choose the AI should go first
	 * @param newState new state of the game
	 * @param newUsername the new username of the player
	 * @return this method will set the value to it is AI's turn
	 * @throws Exception
	 */
	public void playerVsComputerAIFirst(String newState, String newUsername) throws Exception {
		state = newState;
		username1 = newUsername;
		user1 = new User("", 0, username1);//creating the user1 with the inputed username
		AI = new ComputerPlayer(state.substring(3), "");
		user1.setIsTurn(false);
		AI.setIsTurn(true);
	}
	/**
	 * this method is used for when the user choose they want to go first
	 * @param newState new state of the game
	 * @param newUsername the new username of the player
	 * @return this method will set the value to it is player's turn
	 * @throws Exception
	 */
	public void playerVsComputerPlayerFirst(String newState, String newUsername) throws Exception {
		state = newState;
		username1 = newUsername;
		user1 = new User("", 0, username1);//creating the user1 with the inputed username
		AI = new ComputerPlayer(state.substring(3), "");
		user1.setIsTurn(true);
		AI.setIsTurn(false);
	}
	/**
	 * this method will be accepting the question the user asked and return the answer to the question 
	 * @param questionName the question name the user asked
	 * @return the response to the question of the user
	 */
	public String AskAI(String questionName) {
		String newQuestion = questionName;
		user1.setQuestionAsked(newQuestion);//stored the question in the user object
		if (AI.answerQuestion(newQuestion)) {//when the answer is yes
			user1.addQuestionAnswers(true);
			return "Yes";
		}
		//else return no
		user1.addQuestionAnswers(false);
		return "No";
	}
	/**
	 * this method will return the result of the user's guess on the AI's selected character and store who won the game
	 * @param newGuess the guess that the user inputed
	 * @return if the user guessed it right or wrong
	 */
	public String guessAI(String newGuess) {
		String guess = newGuess;
		if (guess.equals(AI.getSelectedCharacter().getName())) {
			gameResult = username1;
			return "Congraulation, " + user1.getUsername() + " you guessed the character, you won!!!!";
		}
		gameResult = "AI";
		return "Sorry, that is the wrong character, the correct one is " + AI.getSelectedCharacter().getName() + ", you lost.";
	}
	/**
	 * this method will check all of the user's answers with their selected character to see if they answered any questions wrong.
	 * Which cause the AI to guess the character wrong or not able to guess the user's character
	 * @return if the user answered any question wrong
	 */
	public boolean checkUserAnswers() {
		boolean check = true;//initialize the check variable, which stores if the user answer all the questions right, to true
		System.out.println("questions: " + AI.getQuestionsAsked().size());
		for (int i = 0; i < AI.getQuestionsAsked().size(); i++) {
			Question curQuestion = AI.getQuestionsAsked().get(i);
			//check if the user's answer is the same and the correct answer to the Ai's question
			if (AI.getGameBoard().getAnswers()[user1.getSelectedCharacter().getCharacterIndex()][curQuestion.getQuestionIndex()] != AI.getQuestionAnswers().get(i)) {
				AI.addQuestionsAnsweredWrong(curQuestion);
				AI.addAnswerQuestionsAnsweredWrong(i);
				check = false;//set check to false
			}
		}
		return check;
	}
	/**
	 * this method will be used to start the game when the players want to randomly choose who goes first
	 * @param usernameO username of the first user
	 * @param birthdayO birthday of the first user
	 * @param usernameS username of the second user
	 * @param birthdayS birthday of the second user
	 * @throws Exception
	 */
	public void playerVsPlayerRandom(String usernameO, int birthdayO, String usernameS, int birthdayS) throws Exception{
		Random rand = new Random();
		username1 = usernameO;
		birthday1 = birthdayO;
		user1 = new User("", birthday1, username1);//initialize the user 1 Object
		username2 = usernameS;
		birthday2 = birthdayS;
		user2 = new User("", birthday2, username2);//initialize the user 2 Object
		int choice = rand.nextInt(2);
		if (choice == 1) {
			user1.setIsTurn(true);
			user2.setIsTurn(false);
		}
		else {
			user1.setIsTurn(false);
			user2.setIsTurn(true);
		}
	}
	/**
	 * this method will be used to start the game when the players want the younger player to go first
	 * @param usernameO username of the first user
	 * @param birthdayO birthday of the first user
	 * @param usernameS username of the second user
	 * @param birthdayS birthday of the second user
	 * @throws Exception
	 */
	public void playerVsPlayerBirthday(String usernameO, int birthdayO, String usernameS, int birthdayS) throws Exception{
		Random rand = new Random();
		username1 = usernameO;
		birthday1 = birthdayO;
		user1 = new User("", birthday1, username1);//initialize the user 1 Object
		username2 = usernameS;
		birthday2 = birthdayS;
		user2 = new User("", birthday2, username2);//initialize the user 2 Object
		int choice = 0;
		if (birthday1 == birthday2) {
			choice = rand.nextInt(2);
		}
		else if (birthday1 > birthday2 || choice == 1) {
			user1.setIsTurn(true);
			user2.setIsTurn(false);
		}
		else {
			user1.setIsTurn(false);
			user2.setIsTurn(true);
		}
	}
	/**
	 * this method will be used to start the game when the players wants user1 to go first
	 * @param usernameO username of the first user
	 * @param birthdayO birthday of the first user
	 * @param usernameS username of the second user
	 * @param birthdayS birthday of the second user
	 * @throws Exception
	 */
	public void playerVsPlayer1First(String usernameO, int birthdayO, String usernameS, int birthdayS) throws Exception{
		username1 = usernameO;
		birthday1 = birthdayO;
		user1 = new User("", birthday1, username1);//initialize the user 1 Object
		username2 = usernameS;
		birthday2 = birthdayS;
		user2 = new User("", birthday2, username2);//initialize the user 2 Object
		user1.setIsTurn(true);
		user2.setIsTurn(false);
	}
	/**
	 * this method will be used to start the game when the players wants user2 to go first
	 * @param usernameO username of the first user
	 * @param birthdayO birthday of the first user
	 * @param usernameS username of the second user
	 * @param birthdayS birthday of the second user
	 * @throws Exception
	 */
	public void playerVsPlayer2First(String usernameO, int birthdayO, String usernameS, int birthdayS) throws Exception{
		username1 = usernameO;
		birthday1 = birthdayO;
		user1 = new User("", birthday1, username1);//initialize the user 1 Object
		username2 = usernameS;
		birthday2 = birthdayS;
		user2 = new User("", birthday2, username2);//initialize the user 2 Object
		user1.setIsTurn(false);
		user2.setIsTurn(true);
	}
	/**
	 * this method will find the User object with username of the player
	 * @param username the username of the User
	 * @return the User object matched with the username
	 */
	public User findUser(String username) {
		if (user1.getUsername().equals(username)) {
			return user1;
		}
		return user2;
	}
	/**
	 * this method will return which player won the game
	 * @return the result of the game
	 */
	public String getGameResult() {
		return gameResult;
	}
	/**
	 * this method will set the result of the game as the inputed result
	 * @param newGameResult the new result of the game
	 */
	public void setGameResult(String newGameResult) {
		gameResult = newGameResult;
	}
}
