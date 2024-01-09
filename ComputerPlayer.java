import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class ComputerPlayer extends Player{
	private String mode;
	private Question askedQuestion;
	private int qIndex;
	private ArrayList<Question> unAskedQuestions = new ArrayList<Question>();
	private ArrayList<Character> possibleCharacters = new ArrayList<Character>();
	private int[] answerCount = new int[getGameBoard().getQuestionSize()];
	public ComputerPlayer(String defaultMode, String defaultState, String defaultGuess) throws Exception {
		super(defaultState, defaultGuess);
		mode = defaultMode;
		unAskedQuestions = getGameBoard().getQuestionsList();
		possibleCharacters = getGameBoard().getCharacters();
		answerCount = getGameBoard().getPeopleCount();
	}
	public String getMode() {
		return mode;
	}
	public void setMode(String newMode) {
		mode = newMode;
	}
	public boolean answerQuestion(String askedQuestion) {
		boolean[][] answersList = getGameBoard().getAnswers();
		int characterIndex = getGameBoard().getCharacters().indexOf(getSelectedCharacter());
		int questionIndex = getGameBoard().findQuestion(askedQuestion).getQuestionIndex();
		if (answersList[characterIndex][questionIndex]) {
			return true;
		}
		return false;
	}
	public String playGuess(String username, boolean guessResult) {
		if (guessResult) {
			return "Congraulation, " + username + ", you won!!!! Because the AI guessed the wrong character";
		}
		return "Sorry, " + username + " the AI guessed your characcter, you lost.";
	}
	public Question playQuestion() {
		Question questionChoosen = chooseQuestion();
		if (mode.equals("easy")) {
			Random rand = new Random();
			qIndex = rand.nextInt(unAskedQuestions.size());
			questionChoosen = getGameBoard().getQuestionsList().get(qIndex);
		}
		setQuestionAsked(questionChoosen.getQuestion());
		return questionChoosen;
	}
	public void askQuestion(String askedQuestion, String questionAnswer) {
		Question newQuestionAsked = getGameBoard().findQuestion(askedQuestion);
		unAskedQuestions.remove(newQuestionAsked);
		String qAnswer = questionAnswer;
		for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {
			if (!possibleCharacters.get(i).getIsActive()) {
				continue;
			}
			if (qAnswer.equals("yes") && !getGameBoard().getAnswers()[i][qIndex]) {
				possibleCharacters.get(i).setIsActive(false);
				reCalculate(i);
			}
			else if (qAnswer.equals("no") && getGameBoard().getAnswers()[i][qIndex]) {
				possibleCharacters.get(i).setIsActive(false);
				reCalculate(i);
			}
		}
		System.out.println("==========================");
		for (int i = 0; i < 24; i++) {
			if (possibleCharacters.get(i).getIsActive()) {
				System.out.println(possibleCharacters.get(i).getName());
			}
		}
	}
	private Question chooseQuestion() {
		Question result = unAskedQuestions.get(0);
		int number = Integer.MAX_VALUE;
		for (int i = 0; i < unAskedQuestions.size(); i++) {
			int questionNumber = unAskedQuestions.get(i).getQuestionIndex();
			int count = Math.abs(answerCount[questionNumber]-possibleCharacters.size()/2);
			if (count < number) {
				number = count;
				result = unAskedQuestions.get(i);
				qIndex = questionNumber;
			}
		}
		return result;
	}
	public boolean onlyOne() {
		int counter = 0;
		for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {
			if (possibleCharacters.get(i).getIsActive()) {
				counter++;
			}
		}
		if (counter == 1) {
			return true;
		}
		return false;
	}
	public String onlyOneLeft() {
		String leftCharacter = "";
		for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {
			if (possibleCharacters.get(i).getIsActive()) {
				leftCharacter = possibleCharacters.get(i).getName();
			}
		}
		return leftCharacter;
	}
	private void reCalculate(int i) {
		for (int j = 0; j < getGameBoard().getQuestionSize(); j++) {
			if (getGameBoard().getAnswers()[i][j]) {
				answerCount[j]--;
			}
		}
	}
	public ArrayList<Character> getPossibleCharacters() {
		return possibleCharacters;
	}
}
