/*Author: Gavin Liu
 * Date: Jan 5 2023
 * Description: this class will have all the original data that was read from the data bases and stored, 
 * which can be called by other classes 
 * */

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class Board {
	private final int characterSize = 24;//number of characters
	private final int questionSize = 19;//number of questions
	private ArrayList<Character> characters = new ArrayList<Character>();//Character type ArrayList storing all the characters
	private ArrayList<Question> questionsList = new ArrayList<Question>();//Question type ArrayList storing all the questions
	private boolean[][] answers = new boolean[characterSize][questionSize];//boolean 2D array storing all the questions answers
	private int[] peopleCount = new int[19];//int array storing the number of character that is true to the 19 questions
	public Board() throws Exception{
		//reading from the QuestionDB csv file for the list of questions
		File file2 = new File("QuestionDB.csv");
		Scanner scanner2 = new Scanner(file2);
		int i = 0;//index of the question
		while (scanner2.hasNextLine()) {
			String line = scanner2.nextLine();
			String[] attributes2 = line.split(",");
			//creating the question object with all of its attributes
			Question question = new Question(
					attributes2[0],			// question name
					attributes2[1],			// category
					attributes2[2],			// attribute of the character
					i						// index
					);
			
			questionsList.add(question);//add the question to the ArrayList
			i++;
		}
		scanner2.close();
		//reading from the GuessWhoDB csv file for all the characters and their attributes
		File file = new File("GuessWhoDB.csv");
		Scanner scanner1 = new Scanner(file);
		i = 0;//index of the character
		while (scanner1.hasNextLine()) {
			String line = scanner1.nextLine();
			String[] attributes = line.split(",");
			//creating the character object and store all of its attributes
			Character character = new Character(
					i,
					attributes[0], 							// name
					attributes[1], 							// eyeColour
					Boolean.parseBoolean(attributes[2]),	// isMale
					Boolean.parseBoolean(attributes[3]),	// isLight
					attributes[4],							// hairColour
					Boolean.parseBoolean(attributes[5]),	// facialHair
					Boolean.parseBoolean(attributes[6]),	// glasses
					Boolean.parseBoolean(attributes[7]),	// visibleTeeth
					Boolean.parseBoolean(attributes[8]),	// wearingHat
					attributes[9], 							// hairLength
					Boolean.parseBoolean(attributes[10])	// IsPiercing
					);
			characters.add(character);//add the character to the ArrayList
			//check if the each question is questions true or false for the player and also increase the value in the peopleCount
			if (character.getEyeColour().equals("Blue")) {
				answers[i][0] = true;
				peopleCount[0]++;
			}
			else if (character.getEyeColour().equals("Brown")) {
				answers[i][1] = true;
				peopleCount[1]++;
			}
			else {
				answers[i][2] = true;
				peopleCount[2]++;
			}
			if (character.getIsMale()) {
				answers[i][3] = true;
				peopleCount[3]++;
			}
			if (character.getIsLight()) {
				answers[i][4] = true;
				peopleCount[4]++;
			}
			if (character.getHairColour().equals("Black")) {
				answers[i][5] = true;
				peopleCount[5]++;
			}
			else if (character.getHairColour().equals("Brown")) {
				answers[i][6] = true;
				peopleCount[6]++;
			}
			else if (character.getHairColour().equals("Ginger")) {
				answers[i][7] = true;
				peopleCount[7]++;
			}
			else if (character.getHairColour().equals("Blonde")) {
				answers[i][8] = true;
				peopleCount[8]++;
			}
			else {
				answers[i][9] = true;
				peopleCount[9]++;
			}
			if (character.getIsFacialHair()) {
				answers[i][10] = true;
				peopleCount[10]++;
			}
			if (character.getIsGlasses()) {
				answers[i][11] = true;
				peopleCount[11]++;
			}
			if (character.getIsTeethVisible()) {
				answers[i][12] = true;
				peopleCount[12]++;
			}
			if (character.getIsHat()) {
				answers[i][13] = true;
				peopleCount[13]++;
			}
			if (character.getHairLength().equals("Short")) {
				answers[i][14] = true;
				peopleCount[14]++;
			}
			else if (character.getHairLength().equals("Long")) {
				answers[i][15] = true;
				peopleCount[15]++;
			}
			else if (character.getHairLength().equals("Tied Up")) {
				answers[i][16] = true;
				peopleCount[16]++;
			}
			else {
				answers[i][17] = true;
				peopleCount[17]++;
			}
			if (character.getIsPiercing()) {
				answers[i][18] = true;
				peopleCount[18]++;
			}
			i++;
		}
		scanner1.close();
	}
	/**
	 * method will return the peopleCount int array
	 * @return peopleCount array
	 */
	public int[] getPeopleCount() {
		return peopleCount;
	}
	/**
	 * method will return the number of questions
	 * @return the number of questions
	 */
	public int getQuestionSize() {
		return questionSize;
	}
	/**
	 * method will return the number of characters
	 * @return the number of characters
	 */
	public int getCharacterSize() {
		return characterSize;
	}
	/**
	 * method will return the ArrayList of questions
	 * @return Question type ArrayList of questions
	 */
	public ArrayList<Question> getQuestionsList() {
		return questionsList;
	}
	/**
	 * method will return the ArrayList of characters
	 * @return Character type ArrayList of characters
	 */
	public ArrayList<Character> getCharacters() {
		return characters;
	}
	/**
	 * method will return the 2D array of questions answers
	 * @return boolean 2D array of answers
	 */
	public boolean[][] getAnswers() {
		return answers;
	}
	/**
	 * method will take the inputed question name and check and find the according question Object and return it
	 * @param questionName the name of the question 
	 * @return the Question type value with the questionName
	 */
	public Question findQuestion(String questionName) {
		Question result = questionsList.get(0);
		for (int i = 1; i < questionsList.size(); i++) {//check every question to see with one have the same question name as the input
			if (questionsList.get(i).getQuestion().equals(questionName)) {//if the current question have the same name as the questionName
				result = questionsList.get(i);//set result to current question
				return result;//then return result
			}
		}
		return result;
	}
}
