import java.io.*;
import java.util.*;

public class Main {
	public static Random rand = new Random();
	public static void main(String[] args) throws Exception{
		
		
		
//           for (int i = 0; i < characterList.size(); i++) {
//           	System.out.println(characterList.get(i).getName());
//           }
//          
		// Board test = new Board();
		// for (int i = 0; i < test.getQuestionsList().size(); i++) {
		// 	System.out.println(test.getQuestionsList().get(i).getQuestion());
		// }
		// User firestar = new User(0, "Start", "", 20231111, "Greninja");
		Game newGame = new Game();
		System.out.println("Welcme to the Guess Who? PvP version. ");
		newGame.playerVsComputer();
//		Board test = new Board();
//		for (int r = 0; r < 24; r++) {
//			for (int c = 0; c < 19; c++) {
//				System.out.print(test.getAnswers()[r][c] + " ");
//			}
//			System.out.println();
//		}
	}
}