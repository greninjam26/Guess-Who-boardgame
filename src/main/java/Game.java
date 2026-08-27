/* Author: Gavin Liu
 * Date: Jan 8 2024
 */
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Coordinates the players, turn order, lifecycle, and outcome of a Guess Who
 * game. A {@code Game} starts in {@link GameStatus#STARTING} and must be
 * initialized with either {@link #startComputerGame} or
 * {@link #startPlayerGame} before play begins.
 */
public class Game {
	private static final String COMPUTER_WINNER = "AI";

	private final Random random;
	private User firstPlayer;
	private User secondPlayer;
	private ComputerPlayer computerPlayer;
	private GameStatus status;
	private Optional<String> winner;

	/**
	 * Creates a game that uses a new random source for randomized turn order.
	 */
	public Game() {
		this(new Random());
	}

	/**
	 * Creates a game with an injected random source.
	 *
	 * @param random source used for randomized turn order
	 * @throws NullPointerException if {@code random} is {@code null}
	 */
	Game(Random random) {
		this.random = Objects.requireNonNull(random, "random");
		status = GameStatus.STARTING;
		winner = Optional.empty();
	}

	/**
	 * Returns the current lifecycle state.
	 *
	 * @return the game status
	 */
	public GameStatus getStatus() {
		return status;
	}

	/**
	 * Returns the winner after the game has finished.
	 *
	 * @return the winning player name, {@code AI}, or an empty value while no
	 *         winner has been recorded
	 */
	public Optional<String> getWinner() {
		return winner;
	}

	/**
	 * Returns the first human player.
	 *
	 * @return the first player, or {@code null} before a game is started
	 */
	public User getFirstPlayer() {
		return firstPlayer;
	}

	/**
	 * Returns the second human player in a player-versus-player game.
	 *
	 * @return the second player, or {@code null} in a computer game or before
	 *         a game is started
	 */
	public User getSecondPlayer() {
		return secondPlayer;
	}

	/**
	 * Returns the computer opponent in a player-versus-computer game.
	 *
	 * @return the computer player, or {@code null} in a two-player game or
	 *         before a game is started
	 */
	public ComputerPlayer getComputerPlayer() {
		return computerPlayer;
	}

	/**
	 * Finds a human player by exact username.
	 *
	 * @param username username to find
	 * @return the matching player
	 * @throws IllegalArgumentException if no current player has that username
	 */
	public User getPlayer(String username) {
		if (firstPlayer != null && firstPlayer.getUsername().equals(username)) {
			return firstPlayer;
		}
		if (secondPlayer != null && secondPlayer.getUsername().equals(username)) {
			return secondPlayer;
		}
		throw new IllegalArgumentException("Unknown player: " + username);
	}

	/**
	 * Starts a player-versus-computer game and assigns the opening turn.
	 *
	 * @param username human player's username
	 * @param difficulty computer difficulty
	 * @param start opening-turn selection
	 * @throws IllegalArgumentException if the username is blank or reserved for
	 *         the computer
	 * @throws NullPointerException if {@code difficulty} or {@code start} is
	 *         {@code null}
	 * @throws Exception if the board resources cannot be loaded
	 */
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

	/**
	 * Starts a two-player game and assigns the opening turn.
	 *
	 * <p>When {@code start} is {@link PlayerGameStart#YOUNGER}, the larger
	 * birthday value is treated as younger. Equal birthdays are resolved
	 * randomly.</p>
	 *
	 * @param firstUsername first player's username
	 * @param firstBirthday first player's birthday value
	 * @param secondUsername second player's username
	 * @param secondBirthday second player's birthday value
	 * @param start opening-turn selection
	 * @throws IllegalArgumentException if either username is blank or both
	 *         usernames are equal
	 * @throws NullPointerException if {@code start} is {@code null}
	 * @throws Exception if the board resources cannot be loaded
	 */
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

	/**
	 * Asks the computer opponent a preset board question and records its answer
	 * in the first player's history.
	 *
	 * @param question exact text of a preset board question
	 * @return {@code Yes} or {@code No}
	 * @throws IllegalArgumentException if the question is not on the board
	 * @throws NullPointerException if no computer opponent has been initialized
	 */
	public String askComputer(String question) {
		boolean answer = computerPlayer.answerQuestion(question);
		firstPlayer.recordQuestionAnswer(question, answer);
		return answer ? "Yes" : "No";
	}

	/**
	 * Resolves the human player's guess against the computer's selected
	 * character and finishes the game.
	 *
	 * @param guess guessed character name
	 * @return a message describing whether the guess was correct
	 * @throws NullPointerException if {@code guess} is {@code null} or no
	 *         computer opponent has been initialized
	 * @throws IllegalStateException if the computer game has already finished
	 */
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

	/**
	 * Checks the answers supplied to the computer against the first player's
	 * selected character. Incorrect answers are added to the computer player's
	 * review history.
	 *
	 * @return {@code true} when every recorded answer is correct
	 * @throws NullPointerException if no computer opponent has been initialized
	 */
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

	/**
	 * Finishes an active game with the supplied winner.
	 *
	 * @param winner a current human player's username, or {@code AI} in a
	 *         player-versus-computer game
	 * @throws IllegalStateException if the game is not in progress
	 * @throws IllegalArgumentException if {@code winner} does not identify a
	 *         participant in the current game
	 */
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
