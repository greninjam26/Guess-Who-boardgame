public class Question {
	private String question;
	private String category;
	private String attribute;
	private int questionIndex;
	public Question(String defaultQuestion, String defaultCategory, String defaultAttribute, int defaultQuestionIndex) {
		question = defaultQuestion;
		category = defaultCategory;
		attribute = defaultAttribute;
		questionIndex = defaultQuestionIndex;
	}
	public String getQuestion() {
		return question;
	}
	public String getCategory() {
		return category;
	}
	public String getAttribute() {
		return attribute;
	}
	public int getQuestionIndex() {
		return questionIndex;
	}
}
