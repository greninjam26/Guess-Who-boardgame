/*Author: Gavin Liu
 * Date: Dec 29 2024
 * Description: this class is made to create the parent class of the ComputerPlayer and the User
 * so this class contain the methods and attributes that are in common for the ComputerPlayer and the User
 * */
import java.util.Random;
import java.util.ArrayList;

public class Player {
	private Question questionAsked;//the new question asked by the player
	private ArrayList<Question> questionsAsked;//questions asked by the player
	private ArrayList<Question> questionsUnAsked;//question haven't asked by the player
	private ArrayList<Boolean> questionAnswers;//the answers the player got from their question
	private ArrayList<Question> questionsAnsweredWrong;//the questions that was answered wrong
	private ArrayList<Boolean> answerQuestionsAnsweredWrong;//the answer of the questions that was answered wrong
	private Character selectedCharacter;//the selected character of the player
	private Board gameBoard;//the gameBoard of the player
	private String questionResult;//the result of the new question asked by the player
	private boolean isTurn;//if it is the player's turn
	public Player(String defaultState) throws Exception{
		//set all the attributes to the default values
		gameBoard = new Board();
		Random rand = new Random();
		questionsAsked = new ArrayList<Question>();
		questionsUnAsked = new ArrayList<Question>();
		questionAnswers = new ArrayList<Boolean>();
		questionsAnsweredWrong = new ArrayList<Question>();
		answerQuestionsAnsweredWrong = new ArrayList<Boolean>();
		selectedCharacter = gameBoard.getCharacters().get(rand.nextInt(24));
		for (int i = 0; i < 19; i++) {
			questionsUnAsked.add(gameBoard.getQuestionsList().get(i));
		}
	}
	/**
	 * this method is used to return the selected character of the player
	 * @return the selected character
	 */
	public Character getSelectedCharacter() {
		return selectedCharacter;
	}
	/**
	 * this method will set the selected character of the player
	 * @param newCharacter the new selected character of the player
	 */
	public void setSelectedCharacter(Character newCharacter) {
		selectedCharacter = newCharacter;
	}
	/**
	 * this method is used to get if it is the player's turn
	 * @return if it is the player's turn
	 */
	public boolean getIsTurn() {
		return isTurn;
	}
	/**
	 * this method will set if it is turn for the player
	 * @param newTurn the new state of if it is the player's turn
	 */
	public void setIsTurn(boolean newTurn) {
		isTurn = newTurn;
	}
	/**
	 * this method will return the questions asked by the player
	 * @return all the questions asked by the player
	 */
	public Question getQuestionAsked() {
		return questionAsked;
	}
	/**
	 * this method will store the new question asked by the player
	 * @param newQuestionAsked the new questions asked by the player
	 */
	public void setQuestionAsked(String newQuestionAsked) {
		questionAsked = gameBoard.findQuestion(newQuestionAsked);
		questionsAsked.add(questionAsked);
		questionsUnAsked.remove(questionAsked);
	}
	/**
	 * this method will return the result of the question
	 * @return the result of the questions
	 */
	public String getQuestionResult() {
		return questionResult;
	}
	/**
	 * this method will set the result of the questions 
	 * @param newQuestionResult the new questions of the question
	 */
	public void setQuestionResult(String newQuestionResult) {
		questionResult = newQuestionResult;
	}
	/**
	 * this method will return the game board of the player
	 * @return the game board used by the player
	 */
	public Board getGameBoard() {
		return gameBoard;
	}
	/**
	 * this method will return the questions that are not asked by the player
	 * @return the questions that was not asked by the player before
	 */
	public ArrayList<Question> getUnAskedQuestions() {
		return questionsUnAsked;
	}
	/**
	 * this method will return the answers that the player got when they asked a questions
	 * @return the answers the player got
	 */
	public ArrayList<Boolean> getQuestionAnswers(){
		return questionAnswers;
	}
	/**
	 * this method add the new answer to the questionsAnswers
	 * @param answer the answer for the new question
	 */
	public void addQuestionAnswers(boolean answer) {
		questionAnswers.add(answer);
	}
	/**
	 * this method will return the question asked by the player
	 * @return the questions asked by the player
	 */
	public ArrayList<Question> getQuestionsAsked() {
		return questionsAsked;
	}
	/**
	 * this method is used for finding the character object from the character name
	 * @param characterName the name of the character
	 * @return the Character Object
	 */
	public Character findCharacter(String characterName) {
		Character result = gameBoard.getCharacters().get(0);
		for (int i = 1; i < gameBoard.getCharacterSize(); i++) {
			Character curCharacter = gameBoard.getCharacters().get(i);
			//check is the current character's name is equals to the character name we are trying to find
			if (curCharacter.getName().equals(characterName)) {
				result = curCharacter;
				return result;
			}
		}
		return result;
	}
	/**
	 * this method will be return the questions that the player's opponent answered wrong
	 * @return the wrong answered questions
	 */
	public ArrayList<Question> getQuestionsAnsweredWrong() {
		return questionsAnsweredWrong;
	}
	/**
	 * this method will be return the question answers that the player's opponent answered wrong
	 * @return the answers of the wrong answered questions
	 */
	public ArrayList<Boolean> getAnswerQuestionsAnsweredWrong() {
		return answerQuestionsAnsweredWrong;
	}
	/**
	 * this method will add in the new question that the player's opponent answered wrong
	 * @param questionAnsweredWrong the question that the opponent answered wrong
	 */
	public void addQuestionsAnsweredWrong(Question questionAnsweredWrong) {
		questionsAnsweredWrong.add(questionAnsweredWrong);
	}
	/**
	 * this method will add in the answer of the new questions that the player's opponent answered wrong
	 * @param questionAnsweredWrongIndex the index of the question that was answered wrong
	 */
	public void addAnswerQuestionsAnsweredWrong(int questionAnsweredWrongIndex) {
		answerQuestionsAnsweredWrong.add(!questionAnswers.get(questionAnsweredWrongIndex));
	}
}
