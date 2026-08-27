import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public class Game {
	private static final String COMPUTER_WINNER = "AI";

	private final Random random;
	private User firstPlayer;
	private User secondPlayer;
	private ComputerPlayer computerPlayer;
	private GameStatus status;
	private Optional<String> winner;

	public Game() {
		this(new Random());
	}

	Game(Random random) {
		this.random = Objects.requireNonNull(random, "random");
		status = GameStatus.STARTING;
		winner = Optional.empty();
	}

	public GameStatus getStatus() {
		return status;
	}

	public Optional<String> getWinner() {
		return winner;
	}

	public User getFirstPlayer() {
		return firstPlayer;
	}

	public User getSecondPlayer() {
		return secondPlayer;
	}

	public ComputerPlayer getComputerPlayer() {
		return computerPlayer;
	}

	public User getPlayer(String username) {
		if (firstPlayer != null && firstPlayer.getUsername().equals(username)) {
			return firstPlayer;
		}
		if (secondPlayer != null && secondPlayer.getUsername().equals(username)) {
			return secondPlayer;
		}
		throw new IllegalArgumentException("Unknown player: " + username);
	}

	public void startComputerGame(String username, ComputerDifficulty difficulty,
			ComputerGameStart start) throws Exception {
		requireUsername(username, "username");
		if (COMPUTER_WINNER.equals(username)) {
			throw new IllegalArgumentException("Username is reserved for the computer: " + username);
		}
		Objects.requireNonNull(difficulty, "difficulty");
		Objects.requireNonNull(start, "start");

		firstPlayer = new User("", 0, username);
		secondPlayer = null;
		computerPlayer = new ComputerPlayer(difficulty.mode(), "");

		boolean playerStarts = switch (start) {
			case PLAYER -> true;
			case COMPUTER -> false;
			case RANDOM -> random.nextBoolean();
		};
		setTurns(firstPlayer, computerPlayer, playerStarts);
		beginGame();
	}

	public void startPlayerGame(String firstUsername, int firstBirthday,
			String secondUsername, int secondBirthday, PlayerGameStart start) throws Exception {
		requireUsername(firstUsername, "firstUsername");
		requireUsername(secondUsername, "secondUsername");
		Objects.requireNonNull(start, "start");
		if (firstUsername.equals(secondUsername)) {
			throw new IllegalArgumentException("Player usernames must be different");
		}

		firstPlayer = new User("", firstBirthday, firstUsername);
		secondPlayer = new User("", secondBirthday, secondUsername);
		computerPlayer = null;

		boolean firstPlayerStarts = switch (start) {
			case FIRST_PLAYER -> true;
			case SECOND_PLAYER -> false;
			case RANDOM -> random.nextBoolean();
			case YOUNGER -> firstBirthday == secondBirthday
					? random.nextBoolean()
					: firstBirthday > secondBirthday;
		};
		setTurns(firstPlayer, secondPlayer, firstPlayerStarts);
		beginGame();
	}

	public String askComputer(String question) {
		boolean answer = computerPlayer.answerQuestion(question);
		firstPlayer.recordQuestionAnswer(question, answer);
		return answer ? "Yes" : "No";
	}

	public String guessComputer(String guess) {
		if (guess.equals(computerPlayer.getSelectedCharacter().getName())) {
			finish(firstPlayer.getUsername());
			return "Congraulation, " + firstPlayer.getUsername()
					+ " you guessed the character, you won!!!!";
		}
		finish(COMPUTER_WINNER);
		return "Sorry, that is the wrong character, the correct one is "
				+ computerPlayer.getSelectedCharacter().getName() + ", you lost.";
	}

	public boolean checkUserAnswers() {
		boolean allCorrect = true;
		for (int i = 0; i < computerPlayer.getQuestionsAsked().size(); i++) {
			Question question = computerPlayer.getQuestionsAsked().get(i);
			boolean correctAnswer = computerPlayer.getGameBoard().getAnswers()
					[firstPlayer.getSelectedCharacter().getCharacterIndex()]
					[question.getQuestionIndex()];
			if (correctAnswer != computerPlayer.getQuestionAnswers().get(i)) {
				computerPlayer.addQuestionsAnsweredWrong(question);
				computerPlayer.addAnswerQuestionsAnsweredWrong(i);
				allCorrect = false;
			}
		}
		return allCorrect;
	}

	public void finish(String winner) {
		if (status != GameStatus.IN_PROGRESS) {
			throw new IllegalStateException("Only a game in progress can be finished");
		}
		boolean knownPlayer = firstPlayer != null && firstPlayer.getUsername().equals(winner)
				|| secondPlayer != null && secondPlayer.getUsername().equals(winner);
		boolean computer = computerPlayer != null && COMPUTER_WINNER.equals(winner);
		if (!knownPlayer && !computer) {
			throw new IllegalArgumentException("Unknown winner: " + winner);
		}
		this.winner = Optional.of(winner);
		status = GameStatus.FINISHED;
	}

	private void beginGame() {
		winner = Optional.empty();
		status = GameStatus.IN_PROGRESS;
	}

	private void setTurns(Player first, Player second, boolean firstStarts) {
		first.setIsTurn(firstStarts);
		second.setIsTurn(!firstStarts);
	}

	private void requireUsername(String username, String fieldName) {
		if (username == null || username.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
	}
}
