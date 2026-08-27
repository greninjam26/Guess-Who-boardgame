public enum ComputerDifficulty {
	EASY("easy"),
	HARD("hard");

	private final String mode;

	ComputerDifficulty(String mode) {
		this.mode = mode;
	}

	String mode() {
		return mode;
	}
}
