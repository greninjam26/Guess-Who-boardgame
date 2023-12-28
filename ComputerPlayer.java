
public class ComputerPlayer extends Player{
	private String mode;
	public ComputerPlayer(String defaultState, String defaultGuess) throws Exception {
		super(defaultState, defaultGuess);
	}
	public String getMode() {
		return mode;
	}
	public void setMode(String newMode) {
		mode = newMode;
	}
	public void easyMode() {
		
	}
	public void mediumMode() {
		
	}
	public void hardMode() {
		
	}
}
