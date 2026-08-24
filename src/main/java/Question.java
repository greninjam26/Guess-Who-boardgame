/*Author: Gavin Liu
 * Date: Dec 29 2023
 * Description: this class is used to store all the attributes of the questions and can be called from other classes
 * */
public class Question {
	//attributes of the question
	private String question;//the question
	private String category;//the category the question belong in
	private String attribute;//the attribute of the character that the question matches
	private int questionIndex;
	public Question(String defaultQuestion, String defaultCategory, String defaultAttribute, int defaultQuestionIndex) {
		//set the attributes to inputed value
		question = defaultQuestion;
		category = defaultCategory;
		attribute = defaultAttribute;
		questionIndex = defaultQuestionIndex;
	}
	/**
	 * this method will return the entire question 
	 * @return the entire question
	 */
	public String getQuestion() {
		return question;
	}
	/**
	 * this method will return which category the questions belong to
	 * @return the category of the question
	 */
	public String getCategory() {
		return category;
	}
	/**
	 * this method will return the attribute of the character what the questions is asking 
	 * @return the attribute of the character the question is matched to
	 */
	public String getAttribute() {
		return attribute;
	}
	/**
	 * this method will return the index of the question index of the data base
	 * @return the index of the question
	 */
	public int getQuestionIndex() {
		return questionIndex;
	}
}
