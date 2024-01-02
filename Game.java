import java.util.Random;
import java.util.Scanner;

public class Game {
	private String gameId;
	private Player currentPlayer;
	private Player[] players;
	private Board board;
	private String state;
	private String username1;
	private int birthday1;
	private User user1;
	private String username2;
	private int birthday2;
	private User user2;
	private ComputerPlayer AI;
	private Scanner sc = new Scanner(System.in);
	public Game() {
		state = "Starting";
		
	}
	public void startGame() {
		
	}
	public void endGame() {
		
	}
	public void switchPlayer() {
		
	}
	public boolean checkWinCondition() {
		return true;
	}
	public String getState() {
		return state;
	}
	public void playerVsComputer() throws Exception {
		Random rand = new Random();
		System.out.println("Please enter your username: ");
		username1 = sc.next();
		System.out.println("Please enter your birthday(yyyymmdd): ");
		birthday1 = sc.nextInt();
		user1 = new User(0, "", "", birthday1, username1);
		System.out.println("Please select the defficultly of the NPC you want to play against(enter a number): 1. easy 2. medium 3. hard");
		int choice = sc.nextInt();
		state = "AI";
		AI = new ComputerPlayer("easy", "", "");
		if (choice == 2) {
			AI = new ComputerPlayer("medium", "", "");
			state = "AI";
		}
		else if (choice == 3) {
			AI = new ComputerPlayer("hard", "", "");
			state = "AI";
		}
		choice = rand.nextInt(2);
		if (choice == 1) {
			user1.setIsTurn(true);
			AI.setIsTurn(false);
		}
		else {
			user1.setIsTurn(false);
			AI.setIsTurn(true);
		}
		while (state.equals("AI")) {
			if (user1.getIsTurn()) {
				System.out.println("Please enter your choice: 1. ask question. 2. guess the character: ");
				System.out.println("+++++++++");
				choice = sc.nextInt();
				if (choice == 1) {
					AskAI();
				}
				else {
					guessAI();
					state = "finished";
				}
				user1.setIsTurn(false);
				AI.setIsTurn(true);
			}
			else {
				state = AI.play(user1);
				System.out.println("\\\\\\\\\\");
				user1.setIsTurn(true);
				AI.setIsTurn(false);
			}
		}
	}
	private void AskAI() {
		System.out.println(user1.getUsername() + ", please enter your selected question: ");
		String newQuestion = sc.next();
		user1.setQuestionAsked(newQuestion);
		if (AI.answerQuestion(newQuestion)) {
			System.out.println("yes");
		}
		else {
			System.out.println("no");
		}
	}
	private void guessAI() {
		System.out.println(user1.getUsername() + ", please enter your guess: ");
		String guess = sc.next();
		if (guess.equals(AI.getSelectedCharacter().getName())) {
			System.out.println("Congraulation, " + user1.getUsername() + " you guessed the character, you won!!!!");
		}
		else {
			System.out.println("Sorry, " + user1.getUsername() + " you guessed the wrong character, you lost.");
		}
	}
	public void playerVsPlayer() throws Exception{
		Random rand = new Random();
		username1 = sc.next();
		birthday1 = sc.nextInt();
		user1 = new User(0, "", "", birthday1, username1);
		username2 = sc.next();
		birthday2 = sc.nextInt();
		user2 = new User(0, "", "", birthday2, username2);
		int choice = 0;
		if (birthday1 == birthday2) {
			choice = rand.nextInt(2);
		}
		else if (birthday1 > birthday2 || choice == 1) {
			user1.setIsTurn(true);
			user2.setIsTurn(false);
		}
		else {
			user1.setIsTurn(false);
			user2.setIsTurn(true);
		}
		if (state.equals("PvP, Free Will")) {
			playerVsPlayerQuestion();
		}
		else if (state.equals("PvP, Select Question")) {
			playerVsPlayerDSelectQuestion();
		}
	}
	private void playerVsPlayerQuestion() {
		while (state.equals("PvP, Free Will")) {
			System.out.println("Please enter your choice: 1. ask question. 2. guess the character: ");
			int choice = sc.nextInt();
			if (user1.getIsTurn()) {
				if (choice == 1) {
					turnAskQuestion(user1, user2);
				}
				else {
					guess(user1, user2);
				}
			}
			else {
				if (choice == 1) {
					turnAskQuestion(user2, user1);
				}
				else {
					guess(user2, user1);
				}
			}
		}
	}
	public void playerVsPlayerDSelectQuestion() {
		while (state.equals("PvP, Select Question")) {
			System.out.println("Please enter your choice: 1. ask question. 2. guess the character: ");
			int choice = sc.nextInt();
			if (user1.getIsTurn()) {
				if (choice == 1) {
					turnSelectQuestion(user1, user2);
				}
				else {
					guess(user1, user2);
				}
			}
			else {
				if (choice == 1) {
					turnSelectQuestion(user2, user1);
				}
				else {
					guess(user2, user1);
				}
			}
		}
	}
	private void turnSelectQuestion(User user1, User user2) {
		System.out.println(user1.getUsername() + ", please enter your selected question: ");
		String newQuestion = sc.next();
		user1.setQuestionAsked(newQuestion);
		System.out.println(user2.getUsername() + ", please answer the question: ");
		String questionResult = sc.next();
		user1.setQuestionResult(questionResult);
		user1.setQuestionCount(user1.getQuestionCount()+1);
	}
	private void turnAskQuestion(User user1, User user2) {
		System.out.println(user1.getUsername() + ", please enter your question: ");
		String newQuestion = sc.next();
		user1.setQuestionAsked(newQuestion);
		System.out.println(user2.getUsername() + ", please answer the question: ");
		String questionResult = sc.next();
		user1.setQuestionResult(questionResult);
		user1.setQuestionCount(user1.getQuestionCount()+1);
	}
	private void guess(User user1, User user2) {
		System.out.println(user1.getUsername() + ", please enter your guess: ");
		String guess = sc.next();
		user1.setGuess(guess);
		boolean check = user1.guessCharacter(user2);
		if (check) {
			System.out.println("Congraulation, " + user1.getUsername() + " you guessed the character, you won!!!!");
		}
		else {
			System.out.println("Congraulation, " + user2.getUsername() + ", you won!!!! \nBecause " + user1.getUsername() + " you guessed the wrong character");
		}
		state = "finished";
	}
}
