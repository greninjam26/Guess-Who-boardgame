/**
 * Lifecycle states of a game session.
 */
public enum GameStatus {
	/** The game has been created but not initialized with players. */
	STARTING,
	/** The game has started and does not yet have a winner. */
	IN_PROGRESS,
	/** The game has ended and has a recorded winner. */
	FINISHED
}
