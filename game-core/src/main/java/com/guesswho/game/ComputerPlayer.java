package com.guesswho.game;

/*Author: Gavin Liu
 * Date: Jan 8 2024
 * Description: this class is made to have most of the logic for the easy and hard AI. all the Ai's guessing
 * and ask Algorism will be here
 * */
import java.util.ArrayList;
import java.util.Random;

/**
 * Computer-controlled player that asks questions and eliminates characters
 * using either an easy random strategy or a hard balanced-split strategy.
 */
public class ComputerPlayer extends Player{
    private String mode;//the mode of the AI, easy or hard
    private ArrayList<Question> unAskedQuestions = new ArrayList<Question>();//the questions that is not asked by the AI
    private ArrayList<Character> possibleCharacters = new ArrayList<Character>();//the characters
    private int[] answerCount = new int[getGameBoard().getQuestionSize()];//the number of possible character that belong in each question
    private int possibleCharactersCount = getGameBoard().getCharacterSize();//the number of characters
    private final Random random;
    /**
     * Creates a computer player using the standard board and a new random
     * source.
     *
     * @param defaultMode computer difficulty mode
     * @param defaultState initial player state
     * @throws Exception if the board resources cannot be loaded
     */
    public ComputerPlayer(String defaultMode, String defaultState) throws Exception {
        this(defaultMode, defaultState, new Random());
    }
    /**
     * Creates a computer player with an injected random source.
     *
     * @param defaultMode computer difficulty mode
     * @param defaultState initial player state
     * @param random source used for random question selection
     * @throws Exception if the board resources cannot be loaded
     */
    public ComputerPlayer(String defaultMode, String defaultState, Random random) throws Exception {
        this(defaultMode, defaultState, new Board(), random);
    }
    ComputerPlayer(String defaultMode, String defaultState, Board board, Random random) {
        super(defaultState, board);
        //the computer's character is genuinely chosen here, not a placeholder
        setSelectedCharacter(board.getCharacters().get(random.nextInt(board.getCharacterSize())));
        mode = defaultMode;
        this.random = random;
        unAskedQuestions.addAll(getGameBoard().getQuestionsList());
        possibleCharacters = new ArrayList<Character>(getGameBoard().getCharacters());
        answerCount = getGameBoard().getPeopleCount().clone();
    }
    /**
     * method will return the mode of the AI
     * @return the mode of the AI
     */
    public String getMode() {
        return mode;
    }
    /**
     * method will set the mode of the AI to the newMode
     * @param newMode the new mode of the AI
     */
    public void setMode(String newMode) {
        mode = newMode;
    }
    /**
     * method will use the inputed question to get the questions and return the answer
     * @param askedQuestion the question user asked
     * @return the answer to the question
     */
    public boolean answerQuestion(String askedQuestion) {
        boolean[][] answersList = getGameBoard().getAnswers();//set answersList to all the answers of the questions matches each characters
        int characterIndex = getGameBoard().getCharacters().indexOf(getSelectedCharacter());
        //use the findQuestion method to find the question object and then get the index
        int questionIndex = getGameBoard().findQuestion(askedQuestion).getQuestionIndex();
        if (answersList[characterIndex][questionIndex]) {
            return true;
        }
        return false;
    }
    /**
     * this method will choose the question to ask randomly if the mode is easy, or it will call the chooseQuestion() method to get the question for the hard mode
     * @return the question that the Ai is asking the user
     */
    public Question playQuestion() {
        Question questionChoosen;
        if (mode.equals("easy")) {//when the mode is easy, pick at random
            questionChoosen = unAskedQuestions.get(random.nextInt(unAskedQuestions.size()));
        }
        else {//hard mode picks the question that splits the field most evenly
            questionChoosen = chooseQuestion();
        }
        setQuestionAsked(questionChoosen.getQuestion());
        return questionChoosen;
    }
    /**
     * method method will take the inputed question asked and the answer, it will record the result, recalculate and update the different array and arrayList that is storing all the values
     * @param askedQuestion the question that was asked by the AI
     * @param questionAnswer the answer the AI got from the user
     */
    public void askQuestion(String askedQuestion, String questionAnswer) {
        Question newQuestionAsked = getGameBoard().findQuestion(askedQuestion);//get the new question asked
        unAskedQuestions.remove(newQuestionAsked);
        int questionIndex = newQuestionAsked.getQuestionIndex();//always filter on the question that was asked
        for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {//for loop though all the characters
            if (!possibleCharacters.get(i).getIsActive()) {//if the character is not active
                continue;//next character
            }
            boolean characterAnswer = getGameBoard().getAnswers()[i][questionIndex];
            if (questionAnswer.equals("yes") && !characterAnswer) {//when the answer is yes
                updateValues(i);
            }
            else if (questionAnswer.equals("no") && characterAnswer) {//when answer is no
                updateValues(i);
            }
        }
    }
    /**
     * this method will choose the question that can eliminate as close to half of the possible characters as possible.
     * @return it will return the questions that the hard AI should ask the user to eliminate half of the possible characters.
     */
    private Question chooseQuestion() {
        Question result = unAskedQuestions.get(0);//set the result to the first question
        int number = Math.abs(answerCount[result.getQuestionIndex()]-possibleCharactersCount/2);//get the value for the first question
        for (int i = 1; i < unAskedQuestions.size(); i++) {//checking all the questions from the second one
            Question candidate = unAskedQuestions.get(i);
            int count = Math.abs(answerCount[candidate.getQuestionIndex()]-possibleCharactersCount/2);//calculate how close the number of questions is to half
            if (count < number) {//if the count is smaller, in other words closer to the half point
                //save the new value
                number = count;
                result = candidate;
            }
        }
        return result;
    }
    /**
     * the method will return if there is only only character is left in the list of possible characters
     * @return {@code true} when exactly one possible character remains active
     */
    boolean onlyOne() {
        int counter = 0;//set the number of possible character to 0
        for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {
            if (possibleCharacters.get(i).getIsActive()) {//check if the character is active
                counter++;//increase the possible character count by 1
            }
        }
        if (counter == 1) {//when there are only one left
            return true;
        }
        return false;
    }
    /**
     * the method will return the last possible character
     * @return the remaining character name, or an empty string if none remain
     */
    String lastOne() {
        String lastCharacterName = "";//initialize the variable that store the name of the last possible character left
        for (int i = 0; i < getGameBoard().getCharacterSize(); i++) {
            if (possibleCharacters.get(i).getIsActive()) {
                lastCharacterName = possibleCharacters.get(i).getName();//set last character to the name of the character
            }
        }
        return lastCharacterName;
    }
    /**
     * the method will recalculate the number of character that is true for each question
     * @param index
     */
    private void reCalculate(int index) {
        for (int j = 0; j < getGameBoard().getQuestionSize(); j++) {
            if (getGameBoard().getAnswers()[index][j]) {//when the character with the index of "index" is true for this question
                answerCount[j]--;//decrease the count by 1
            }
        }
    }
    /**
     * this method will update the values related to possible characters
     * @param index the index of the character
     */
    private void updateValues(int index) {
        possibleCharacters.get(index).setIsActive(false);//set the character that is no to not active
        possibleCharactersCount--;//reduce the number of possible characters by 1
        reCalculate(index);//recalculate the number of character that is true to each question
    }
    /**
     * this method will return the possible characters ArrayList
     * @return the list of possible characters
     */
    public ArrayList<Character> getPossibleCharacters() {
        return possibleCharacters;
    }
}
