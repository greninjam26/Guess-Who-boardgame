public class Question {
	private String question;
	private String category;
	private String attribute;
	public Question(String defaultQuestion, String defaultCategory, String defaultAttribute) {
		question = defaultQuestion;
		category = defaultCategory;
		attribute = defaultAttribute;
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
	
}
