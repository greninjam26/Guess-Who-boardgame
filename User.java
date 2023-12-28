public class User extends Player{
	private String username;
	private int birthday;
	private int questionCount;
	private int score;
	public User(int defaultScore, String defaultState, String defaultGuess, int defaultBirthday, String defaultUsername) throws Exception{
		super(defaultState, defaultGuess);
		score = defaultScore;
		birthday = defaultBirthday;
		username = defaultUsername;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String newUsername) {
		username = newUsername;
	}
	public int getBirthday() {
		return birthday;
	}
	public void setBirthday(int newBirthday) {
		birthday = newBirthday;
	}
	public int getScore() {
		return score;
	}
	public int calculateScore() {
		//score = score calculation
		return score;
	}
	public int getQuestionCount() {
		return questionCount;
	}
	public void setQuestionCount(int newQuestionCount) {
		questionCount = newQuestionCount;
	}
}
