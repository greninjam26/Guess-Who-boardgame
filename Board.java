import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class Board {
	private final int characterSize = 24;
	private final int questionSize = 19;
	private ArrayList<Character> characters = new ArrayList<Character>();
	private ArrayList<Question> questionsList = new ArrayList<Question>();
	private boolean[][] answers = new boolean[characterSize][questionSize];
	private int[] peopleCount = new int[19];
	public Board() throws Exception{
		File file2 = new File("QuestionDB.csv");
		Scanner scanner2 = new Scanner(file2);
		int i = 0;
		while (scanner2.hasNextLine()) {
			String line = scanner2.nextLine();
			String[] attributes2 = line.split(",");
			
			Question question = new Question(
					attributes2[0],
					attributes2[1],
					attributes2[2],
					i
					);
			
			questionsList.add(question);
			i++;
		}
		scanner2.close();
		File file = new File("GuessWhoDB.csv");
		Scanner scanner1 = new Scanner(file);
		i = 0;
		while (scanner1.hasNextLine()) {
			String line = scanner1.nextLine();
			String[] attributes = line.split(",");
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
			characters.add(character);
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
			// Now you can use 'character' as needed (e.g., add it to a list)
			i++;
		}
		scanner1.close();
	}
	public int[] getPeopleCount() {
		return peopleCount;
	}
	public int getQuestionSize() {
		return questionSize;
	}
	public int getCharacterSize() {
		return characterSize;
	}
	public ArrayList<Question> getQuestionsList() {
		return questionsList;
	}
	public ArrayList<Character> getCharacters() {
		return characters;
	}
	public boolean[][] getAnswers() {
		return answers;
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
