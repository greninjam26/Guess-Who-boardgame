import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class Board {
	private ArrayList<Character> characters = new ArrayList<Character>();
	private ArrayList<Question> questionsList = new ArrayList<Question>();
	public Board() throws Exception{
		File file = new File("GuessWhoDB.csv");
		Scanner scanner1 = new Scanner(file);
		while (scanner1.hasNextLine()) {
			String line = scanner1.nextLine();
			String[] attributes = line.split(",");
			Character character = new Character(
					attributes[0], 							// name
					attributes[1],  						// eyeColour
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
			characters.add(character);
			// Now you can use 'character' as needed (e.g., add it to a list)
		}
		scanner1.close();
		File file2 = new File("QuestionDB.csv");
		Scanner scanner2 = new Scanner(file2);
		while (scanner2.hasNextLine()) {
			String line = scanner2.nextLine();
			String[] attributes2 = line.split(",");
			
			Question question = new Question(
					attributes2[0],
					attributes2[1],
					attributes2[2]
					);
			
			questionsList.add(question);
		}
		scanner2.close();
	}
	public ArrayList<Question> getQuestionsList() {
		return questionsList;
	}
	public ArrayList<Character> getCharacters() {
		return characters;
	}
	public Question findQuestion(String questionName) {
		Question result = questionsList.get(0);
		for (int i = 0; i < questionsList.size(); i++) {
			if (questionsList.get(i).getQuestion().equals(questionName)) {
				result = questionsList.get(i);
				return result;
			}
		}
		return result;
	}
}
