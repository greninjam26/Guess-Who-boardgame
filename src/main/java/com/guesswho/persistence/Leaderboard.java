package com.guesswho.persistence;

import java.util.*;
import java.io.*;

/**
 * Loads, updates, and sorts player names and scores for the leaderboard.
 */
public class Leaderboard {

	private ArrayList<Integer> scores = new ArrayList<Integer>();
	private ArrayList<String> names = new ArrayList<String>();

	/**
	 * Creates an empty leaderboard.
	 *
	 * @throws Exception retained for compatibility with existing callers
	 */
	public Leaderboard()throws Exception{

	}
	/**
	 * Loads name and score pairs from {@code Leaderboard.csv}.
	 *
	 * @throws Exception if the leaderboard file cannot be read or contains an
	 *         invalid score
	 */
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
	/**
	 * Adds a name and score to the in-memory leaderboard.
	 *
	 * @param passedName player name
	 * @param passedScore player score
	 */
	public void addScore(String passedName, int passedScore){
		names.add(passedName);
		scores.add(passedScore);
	}
	/**
	 * Sorts leaderboard entries into ascending score order while keeping names
	 * paired with their scores.
	 */
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
