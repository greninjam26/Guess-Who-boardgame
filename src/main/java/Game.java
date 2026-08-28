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
	private Question pendingComputerQuestion;
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
	 * Returns the participant whose turn is currently active.
	 *
	 * @return a human player's username or {@code AI}
	 * @throws IllegalStateException if no game is in progress
	 */
	public String getCurrentPlayerName() {
		requireInProgress();
		if (firstPlayer.getIsTurn()) {
			return firstPlayer.getUsername();
		}
		return secondPlayer != null ? secondPlayer.getUsername() : COMPUTER_WINNER;
	}

	/**
	 * Returns the preset question texts that the current human player has not
	 * asked.
	 *
	 * @return unasked question texts in board order
	 * @throws IllegalStateException if no game is in progress or the current
	 *         participant is the computer
	 */
	public String[] getCurrentPlayerQuestionTexts() {
		User player = requireCurrentHumanPlayer();
		return player.getUnAskedQuestions().stream()
				.map(Question::getQuestion)
				.toArray(String[]::new);
	}

	/**
	 * Returns the character names available on the game board.
	 *
	 * @return character names in board order
	 * @throws IllegalStateException if the game has not started
	 */
	public String[] getCharacterNames() {
		if (firstPlayer == null) {
			throw new IllegalStateException("Game must be started before character names are available");
		}
		return firstPlayer.getGameBoard().getCharacters().stream()
				.map(Character::getName)
				.toArray(String[]::new);
	}

	/**
	 * Selects a board character for a human player.
	 *
	 * @param username username of the player selecting the character
	 * @param characterName exact board character name
	 * @throws IllegalArgumentException if the username or character name is
	 *         unknown
	 * @throws IllegalStateException if the game has not finished
	 */
	public void selectCharacter(String username, String characterName) {
		requireFinished();
		User player = getPlayer(username);
		Character character = player.findCharacter(characterName);
		player.setSelectedCharacter(character);
	}

	/**
	 * Returns the board index of a human player's selected character.
	 *
	 * @param username username of the player whose character was selected
	 * @return selected character's board index
	 * @throws IllegalArgumentException if the username is unknown
	 * @throws IllegalStateException if the game has not finished
	 */
	public int getSelectedCharacterIndex(String username) {
		requireFinished();
		return getPlayer(username).getSelectedCharacter().getCharacterIndex();
	}

	/**
	 * Records a human player's question and the answer they received.
	 *
	 * @param username username of the player who asked the question
	 * @param question question text, including free-form questions
	 * @param answer answer received for the question
	 * @throws IllegalArgumentException if {@code username} is not a current
	 *         human player
	 * @throws IllegalStateException if no game is in progress or it is not that
	 *         player's turn
	 */
	public void recordPlayerQuestion(String username, String question, boolean answer) {
		requireInProgress();
		User player = getPlayer(username);
		requireTurn(player);
		player.recordQuestionAnswer(question, answer);
	}

	/**
	 * Transfers the active turn to the other participant.
	 *
	 * @throws IllegalStateException if no game is in progress or the computer
	 *         has an unanswered question
	 */
	public void advanceTurn() {
		requireInProgress();
		if (pendingComputerQuestion != null) {
			throw new IllegalStateException("The computer question must be answered first");
		}
		Player opponent = computerPlayer != null ? computerPlayer : secondPlayer;
		setTurns(firstPlayer, opponent, !firstPlayer.getIsTurn());
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
	 * @throws IllegalStateException if no computer game is in progress or it is
	 *         not the human player's turn
	 */
	public String askComputer(String question) {
		ComputerPlayer computer = requireComputerPlayer();
		requireTurn(firstPlayer);
		boolean answer = computer.answerQuestion(question);
		firstPlayer.recordQuestionAnswer(question, answer);
		advanceTurn();
		return answer ? "Yes" : "No";
	}

	/**
	 * Selects and records the computer opponent's next question.
	 *
	 * @return the question selected by the computer
	 * @throws IllegalStateException if no computer game is in progress, it is
	 *         not the computer's turn, another question is pending, or no
	 *         questions remain
	 */
	public Question playComputerQuestion() {
		ComputerPlayer computer = requireComputerPlayer();
		requireTurn(computer);
		if (pendingComputerQuestion != null) {
			throw new IllegalStateException("The computer already has a pending question");
		}
		if (computer.getUnAskedQuestions().isEmpty()) {
			throw new IllegalStateException("The computer has no questions remaining");
		}
		pendingComputerQuestion = computer.playQuestion();
		return pendingComputerQuestion;
	}

	/**
	 * Applies the human player's answer to the computer's current question,
	 * records the answer, and returns the turn to the human player.
	 *
	 * @param answer answer to the computer's current question
	 * @throws IllegalStateException if no computer game is in progress, it is
	 *         not the computer's turn, or the computer has no pending question
	 */
	public void answerComputerQuestion(boolean answer) {
		ComputerPlayer computer = requireComputerPlayer();
		requireTurn(computer);
		if (pendingComputerQuestion == null) {
			throw new IllegalStateException("The computer has not asked a question");
		}
		computer.askQuestion(
				pendingComputerQuestion.getQuestion(), answer ? "yes" : "no");
		computer.addQuestionAnswers(answer);
		pendingComputerQuestion = null;
		advanceTurn();
	}

	/**
	 * Resolves the human player's guess against the computer's selected
	 * character and finishes the game.
	 *
	 * @param guess guessed character name
	 * @return a message describing whether the guess was correct
	 * @throws IllegalArgumentException if {@code guess} is {@code null} or blank
	 * @throws IllegalStateException if no computer game is in progress or it is
	 *         not the human player's turn
	 */
	public String guessComputer(String guess) {
		ComputerPlayer computer = requireComputerPlayer();
		requireTurn(firstPlayer);
		if (guess == null || guess.isBlank()) {
			throw new IllegalArgumentException("guess must not be blank");
		}
		if (guess.equals(computer.getSelectedCharacter().getName())) {
			finish(firstPlayer.getUsername());
			return "Congraulation, " + firstPlayer.getUsername()
					+ " you guessed the character, you won!!!!";
		}
		finish(COMPUTER_WINNER);
		return "Sorry, that is the wrong character, the correct one is "
				+ computer.getSelectedCharacter().getName() + ", you lost.";
	}

	/**
	 * Resolves a human player's guess in a player-versus-player game and
	 * finishes the game with the appropriate winner.
	 *
	 * @param username username of the player making the guess
	 * @param characterName exact board character name being guessed
	 * @param correct whether the opposing player confirmed the guess
	 * @return the winning player's username
	 * @throws IllegalArgumentException if the username or character name is
	 *         unknown
	 * @throws IllegalStateException if no player-versus-player game is in
	 *         progress or it is not the named player's turn
	 */
	public String resolvePlayerGuess(String username, String characterName, boolean correct) {
		requirePlayerGame();
		User guessingPlayer = getPlayer(username);
		requireTurn(guessingPlayer);
		guessingPlayer.findCharacter(characterName);
		User opponent = guessingPlayer == firstPlayer ? secondPlayer : firstPlayer;
		String winningUsername = correct ? username : opponent.getUsername();
		finish(winningUsername);
		return winningUsername;
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
		pendingComputerQuestion = null;
		status = GameStatus.IN_PROGRESS;
	}

	private void setTurns(Player first, Player second, boolean firstStarts) {
		first.setIsTurn(firstStarts);
		second.setIsTurn(!firstStarts);
	}

	private ComputerPlayer requireComputerPlayer() {
		requireInProgress();
		if (computerPlayer == null) {
			throw new IllegalStateException("No computer game is in progress");
		}
		return computerPlayer;
	}

	private void requirePlayerGame() {
		requireInProgress();
		if (secondPlayer == null) {
			throw new IllegalStateException("No player-versus-player game is in progress");
		}
	}

	private User requireCurrentHumanPlayer() {
		requireInProgress();
		if (firstPlayer.getIsTurn()) {
			return firstPlayer;
		}
		if (secondPlayer != null && secondPlayer.getIsTurn()) {
			return secondPlayer;
		}
		throw new IllegalStateException("The current participant is not a human player");
	}

	private void requireTurn(Player player) {
		requireInProgress();
		if (!player.getIsTurn()) {
			throw new IllegalStateException("It is not this player's turn");
		}
	}

	private void requireInProgress() {
		if (status != GameStatus.IN_PROGRESS) {
			throw new IllegalStateException("No game is in progress");
		}
	}

	private void requireFinished() {
		if (status != GameStatus.FINISHED) {
			throw new IllegalStateException("Game must be finished before characters can be selected or revealed");
		}
	}

	private void requireUsername(String username, String fieldName) {
		if (username == null || username.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
	}
}
