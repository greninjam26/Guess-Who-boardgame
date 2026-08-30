package com.guesswho.game;

/*Author: Gavin Liu
 * Date: Dec 29 2023
 * Description: this class is used to create the user Object which all the attributes tha user will have,
 * but the ComputerPlayer don't have
 * */
/**
 * Human player with a username and birthday value used by game setup.
 */
public class User extends Player{
    private CharacterCommitment commitment;//the promise made when the character was chosen
    //all the attributes
    private String username;//the username of the user
    private int birthday;//the birthday of the user
    /**
     * Creates a human player using the standard board.
     *
     * @param defaultState initial player state
     * @param defaultBirthday birthday value used for younger-player turn order
     * @param defaultUsername displayed player name
     * @throws Exception if the board resources cannot be loaded
     */
    public User(String defaultState, int defaultBirthday, String defaultUsername) throws Exception{
        super(defaultState);//call the super class
        //set the attributes to inputed values
        birthday = defaultBirthday;
        username = defaultUsername;
    }
    /**
     * this method will return the username of the User
     * @return the username
     */
    public String getUsername() {
        return username;
    }
    /**
     * this method will set the new username for the User
     * @param newUsername the new username
     */
    public void setUsername(String newUsername) {
        username = newUsername;
    }
    /**
     * this method will return the birthday of the User
     * @return the birthday
     */
    public int getBirthday() {
        return birthday;
    }
    /**
     * this method will set the birthday of the User
     * @param newBirthday the new birthday
     */
    public void setBirthday(int newBirthday) {
        birthday = newBirthday;
    }
    /**
     * Returns the promise made when this player chose their character.
     *
     * @return the commitment, or {@code null} before a character is chosen
     */
    public CharacterCommitment getCommitment() {
        return commitment;
    }
    /**
     * Records the promise made when choosing a character.
     *
     * @param newCommitment the commitment made at the moment of choosing
     */
    public void setCommitment(CharacterCommitment newCommitment) {
        commitment = newCommitment;
    }
}
