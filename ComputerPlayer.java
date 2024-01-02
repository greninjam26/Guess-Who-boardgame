import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

public class ComputerPlayer extends Player{
	private String mode;
	private Question askedQuestion;
	private int qIndex;
	private Scanner scanner = new Scanner(System.in);
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
	public String play(User user1) {
		System.out.println("Please answer the Question(yes or no): ");
		if (onlyOne() == 1) {
			if (guess()) {
				System.out.println("Congraulation, " + user1.getUsername() + ", you won!!!! Because the AI guessed the wrong character");
			}
			else {
				System.out.println("Sorry, " + user1.getUsername() + " the AI guessed your characcter, you lost.");
			}
			return "finished";
		}
		if (mode.equals("easy")) {
			easyMode();
		}
		else if (mode.equals("medium")) {
			mediumMode();
		}
		else {
			hardMode();
		}
		return "AI";
	}
	private void easyMode() {
		Random rand = new Random();
		qIndex = rand.nextInt(unAskedQuestions.size());
		askedQuestion = getGameBoard().getQuestionsList().get(qIndex);
		askQuestion(askedQuestion);
	}
	private void mediumMode() {
		
	}
	private void hardMode() {
		askedQuestion = chooseQuestion();
		askQuestion(askedQuestion);
	}
	private void askQuestion(Question askedQuestion) {
		System.out.println(askedQuestion.getQuestion());
		unAskedQuestions.remove(askedQuestion);
		String qAnswer = scanner.next();
		for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {
			if (!possibleCharacters.get(i).getIsActive()) {
				continue;
			}
			if (qAnswer.equals("yes") && !getGameBoard().getAnswers()[i][qIndex]) {
				possibleCharacters.get(i).setIsActive(false);
			}
			else if (qAnswer.equals("no") && getGameBoard().getAnswers()[i][qIndex]) {
				possibleCharacters.get(i).setIsActive(false);
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
	private boolean guess() {
		System.out.println("Is " + possibleCharacters.get(0).getName() + " the character? ");
		String choice = scanner.next();
		if (choice.equals("yes")) {
			return false;
		}
		return true;
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
	private int onlyOne() {
		int counter = 0;
		for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {
			if (possibleCharacters.get(i).getIsActive()) {
				counter++;
			}
		}
		return counter;
	}
}
