import java.util.*;
import java.io.*;
public class Leaderboard {
	
	private ArrayList<Integer> scores = new ArrayList<Integer>();
	private ArrayList<String> names = new ArrayList<String>();
	
	public Leaderboard()throws Exception{
		
	}
	public void readLeaderboard()throws Exception{
		File file = new File("Leaderboard.csv");
		Scanner scanner2 = new Scanner(file);
		while(scanner2.hasNextLine()){
			String line = scanner2.nextLine();
			String[] rank = line.split(",");
			names.add(rank[0]);
			scores.add(Integer.parseInt(rank[1]));
		}
	}
	public void addScore(String passedName, int passedScore){
		names.add(passedName);
		scores.add(passedScore);
	}
	public void sortLeaderboard(){
		int temp;
        String nameTemp; 
        int i = 0;
        boolean check = true;
        while(check == true){
            check = false;
            for(int j = 0; j < scores.size()-i-1; j++){
                if(scores.get(j)>scores.get(j+1)){
                    temp = scores.get(j);
                    scores.set(j,scores.get(j+1));
                    scores.set(j+1,temp);
                    nameTemp = names.get(j);
                    names.set(j,names.get(j+1));
                    names.set(j+1,nameTemp);
                    check = true;
                }
            }
            i++; 
        }
		
	}

}
