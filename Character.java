public class Character {
	private int characterIndex;
	private String name;
	private String eyeColour;
	private boolean isMale;
	private boolean isLight;
	private String hairColour;
	private boolean isFacialHair;
	private boolean isGlasses;
	private boolean isTeethVisible;
	private boolean isHat;
	private String hairLength;
	private boolean isPiercing;
	private boolean isActive;
	public Character(int defaultCharacterIndex, String defaultName, String defaultEyeColour, boolean defaultIsMale, 
			boolean defaultIsLight, String defaultHairColour, boolean defaultIsFacialHair, 
			boolean defaultIsGlasses, boolean defaultIsTeethVisible, boolean defaultIsHat, 
			String defaultHairLength, boolean defaultIsPiercing) {
		characterIndex = defaultCharacterIndex;
		name = defaultName;
		eyeColour = defaultEyeColour;
		isMale = defaultIsMale;
		isLight = defaultIsLight;
		hairColour = defaultHairColour;
		isFacialHair = defaultIsFacialHair;
		isGlasses = defaultIsGlasses;
		isTeethVisible = defaultIsTeethVisible;
		isHat = defaultIsHat;
		hairLength = defaultHairLength;
		isPiercing = defaultIsPiercing;
		isActive = true;
	}
	public int getCharacterIndex() {
		return characterIndex;
	}
	public String getName() {
		return name;
	}
	public String getEyeColour() {
		return eyeColour;
	}
	public boolean getIsMale() {
		return isMale;
	}
	public boolean getIsLight() {
		return isLight;
	}
	public String getHairColour() {
		return hairColour;
	}
	public boolean getIsFacialHair() {
		return isFacialHair;
	}
	public boolean getIsGlasses() {
		return isGlasses;
	}
	public boolean getIsTeethVisible() {
		return isTeethVisible;
	}
	public boolean getIsHat() {
		return isHat;
	}
	public String getHairLength() {
		return hairLength;
	}
	public boolean getIsPiercing() {
		return isPiercing;
	}
	public boolean getIsActive() {
		return isActive;
	}
	public void setIsActive(boolean newIsActive) {
		isActive = newIsActive;
	}
}
