/*Author: Gavin Liu
 * Date: Dec 29 2023
 * Description: this class is used to create the user Object which all the attributes tha user will have, 
 * but the ComputerPlayer don't have
 * */
public class User extends Player{
	//all the attributes
	private String username;//the username of the user
	private int birthday;//the birthday of the user
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
}
