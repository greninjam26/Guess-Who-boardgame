package com.guesswho.game;

/*Author: Gavin Liu
 * Date: Dec 29 2024
 * Description: this class is made to create the parent class of the ComputerPlayer and the User
 * so this class contain the methods and attributes that are in common for the ComputerPlayer and the User
 * */
import java.util.Random;
import java.util.ArrayList;

/**
 * Holds the board state and question history shared by human and computer
 * players.
 */
public class Player {
	private Question questionAsked;//the new question asked by the player
	private ArrayList<Question> questionsAsked;//questions asked by the player
	private ArrayList<Question> questionsUnAsked;//question haven't asked by the player
	private ArrayList<Boolean> questionAnswers;//the answers the player got from their question
	private Character selectedCharacter;//the selected character of the player
	private Board gameBoard;//the gameBoard of the player
	private String questionResult;//the result of the new question asked by the player
	private boolean isTurn;//if it is the player's turn
	/**
	 * Creates a player using the standard board and a randomly selected
	 * character.
	 *
	 * @param defaultState initial state retained for compatibility with player
	 *        subclasses
	 * @throws Exception if the board resources cannot be loaded
	 */
	public Player(String defaultState) throws Exception{
		this(defaultState, new Board(), new Random());
	}
	Player(String defaultState, Board board, Random random) {
		//set all the attributes to the default values
		gameBoard = board;
		questionsAsked = new ArrayList<Question>();
		questionsUnAsked = new ArrayList<Question>();
		questionAnswers = new ArrayList<Boolean>();
		selectedCharacter = gameBoard.getCharacters().get(random.nextInt(gameBoard.getCharacters().size()));
		questionsUnAsked.addAll(gameBoard.getQuestionsList());
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
	 * Records a question and its answer as one history entry. Free-form questions
	 * are retained even when they are not part of the preset board questions.
	 * Preset questions are also removed from the player's unasked-question list.
	 *
	 * @param question the question that was asked
	 * @param answer the answer received for the question
	 */
	public void recordQuestionAnswer(String question, boolean answer) {
		try {
			setQuestionAsked(question);
		}
		catch (IllegalArgumentException exception) {
			questionAsked = new Question(question, "free-form", "", -1);
			questionsAsked.add(questionAsked);
		}
		addQuestionAnswers(answer);
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
	 * @throws IllegalArgumentException if the character name is unknown
	 */
	public Character findCharacter(String characterName) {
		for (Character character : gameBoard.getCharacters()) {
			if (character.getName().equals(characterName)) {
				return character;
			}
		}
		throw new IllegalArgumentException("Unknown character: " + characterName);
	}
}
