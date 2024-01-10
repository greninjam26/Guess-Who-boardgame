import java.util.Random;
import java.util.ArrayList;

public abstract class Player {
	private Question questionAsked;
	private ArrayList<Question> questionsAsked = new ArrayList<Question>();
	private ArrayList<Question> questionsUnAsked = new ArrayList<Question>();
	private ArrayList<Boolean> questionAnswers = new ArrayList<Boolean>();
	private ArrayList<Question> questionsAnsweredWrong = new ArrayList<Question>();
	private ArrayList<Boolean> answerQuestionsAnsweredWrong = new ArrayList<Boolean>();
	private Character selectedCharacter;
	private Board gameBoard;
	private String questionResult;
	private String guess;
	private boolean isTurn;
	public Player(String defaultState, String defaultGuess) throws Exception{
		guess = defaultGuess;
		gameBoard = new Board();
		Random rand = new Random();
		selectedCharacter = gameBoard.getCharacters().get(rand.nextInt(24));
		for (int i = 0; i < 19; i++) {
			questionsUnAsked.add(gameBoard.getQuestionsList().get(i));
		}
	}
	public boolean guessCharacter(User opponent) {
		if (guess.equals(opponent.getSelectedCharacter().getName())) {
			return true;
		}
		return false;
	}
	public Character getSelectedCharacter() {
		return selectedCharacter;
	}
	public void setSelectedCharacter(Character newCharacter) {
		selectedCharacter = newCharacter;
	}
	public void setGuess(String newGuess) {
		guess = newGuess;
	}
	public boolean getIsTurn() {
		return isTurn;
	}
	public void setIsTurn(boolean newTurn) {
		isTurn = newTurn;
	}
	public Question getQuestionAsked() {
		return questionAsked;
	}
	public void setQuestionAsked(String newQuestionAsked) {
		questionAsked = gameBoard.findQuestion(newQuestionAsked);
		questionsAsked.add(questionAsked);
		questionsUnAsked.remove(questionAsked);
	}
	public String getQuestionResult() {
		return questionResult;
	}
	public void setQuestionResult(String newQuestionResult) {
		questionResult = newQuestionResult;
	}
	public Board getGameBoard() {
		return gameBoard;
	}
	public ArrayList<Question> getUnAskedQuestions() {
		return questionsUnAsked;
	}
	public ArrayList<Boolean> getQuestionAnswers(){
		return questionAnswers;
	}
	public void addQuestionAnswers(boolean answer) {
		questionAnswers.add(answer);
	}
	public ArrayList<Question> getQuestionsAsked() {
		return questionsAsked;
	}
	public Character findCharacter(String characterName) {
		Character result = gameBoard.getCharacters().get(0);
		for (int i = 1; i < gameBoard.getCharacterSize(); i++) {
			Character curCharacter = gameBoard.getCharacters().get(i);
			if (curCharacter.getName().equals(characterName)) {
				result = curCharacter;
				break;
			}
		}
		return result;
	}
	public ArrayList<Question> getQuestionsAnsweredWrong() {
		return questionsAnsweredWrong;
	}
	public ArrayList<Boolean> getAnswerQuestionsAnsweredWrong() {
		return answerQuestionsAnsweredWrong;
	}
	public void addQuestionsAnsweredWrong(Question questionAnsweredWrong) {
		questionsAnsweredWrong.add(questionAnsweredWrong);
	}
	public void addAnswerQuestionsAnsweredWrong(int questionAnsweredWrongIndex) {
		answerQuestionsAnsweredWrong.add(questionAnswers.get(questionAnsweredWrongIndex));
	}
}
