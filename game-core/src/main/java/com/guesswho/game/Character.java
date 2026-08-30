package com.guesswho.game;

/*Author: Gavin Liu
 * Date: Dec 29 2023
 * Description: this class is used to store all the attributes of the character and can be called from other classes
 * */
/**
 * Describes a Guess Who character and whether the character remains active on
 * a player's board.
 */
public class Character {
    //add the attributes of the character
    private int characterIndex;			//the index of the character in the database
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
    /**
     * Creates a character with all attributes loaded from the character data.
     *
     * @param defaultCharacterIndex zero-based position in the character data
     * @param defaultName character name
     * @param defaultEyeColour eye colour
     * @param defaultIsMale whether the character is male
     * @param defaultIsLight whether the character has a light skin tone
     * @param defaultHairColour hair colour
     * @param defaultIsFacialHair whether the character has facial hair
     * @param defaultIsGlasses whether the character wears glasses
     * @param defaultIsTeethVisible whether the character has visible teeth
     * @param defaultIsHat whether the character wears a hat
     * @param defaultHairLength hair-length category
     * @param defaultIsPiercing whether the character has a piercing
     */
    public Character(int defaultCharacterIndex, String defaultName, String defaultEyeColour, boolean defaultIsMale,
            boolean defaultIsLight, String defaultHairColour, boolean defaultIsFacialHair,
            boolean defaultIsGlasses, boolean defaultIsTeethVisible, boolean defaultIsHat,
            String defaultHairLength, boolean defaultIsPiercing) {
        //set everything to inputed or default value
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
    }
    /**
     * this method will return the index of the character
     * @return the index the character
     */
    public int getCharacterIndex() {
        return characterIndex;
    }
    /**
     * this method will return the name of the character
     * @return the name the character
     */
    public String getName() {
        return name;
    }
    /**
     * this method will return the eye colour of the character
     * @return the eye colour the character
     */
    public String getEyeColour() {
        return eyeColour;
    }
    /**
     * this method will return if the character is male
     * @return if the character is male
     */
    public boolean getIsMale() {
        return isMale;
    }
    /**
     * this method will return if the character is light skin tone
     * @return if the character is light skin tone
     */
    public boolean getIsLight() {
        return isLight;
    }
    /**
     * this method will return the hair colour of the character
     * @return the the hair colour the character
     */
    public String getHairColour() {
        return hairColour;
    }
    /**
     * this method will return if the character have facial hair
     * @return if the character have facial hair
     */
    public boolean getIsFacialHair() {
        return isFacialHair;
    }
    /**
     * this method will return if the character is wearing glasses
     * @return if the character is wearing glasses
     */
    public boolean getIsGlasses() {
        return isGlasses;
    }
    /**
     * this method will return if the character have visible teeth
     * @return if the character have visible teech
     */
    public boolean getIsTeethVisible() {
        return isTeethVisible;
    }
    /**
     * this method will return if the character have hat on
     * @return if the character have hat on
     */
    public boolean getIsHat() {
        return isHat;
    }
    /**
     * this method will return the hair length of the character
     * @return the the hair length the character
     */
    public String getHairLength() {
        return hairLength;
    }
    /**
     * this method will return if the character have piercing
     * @return if the character have piercing
     */
    public boolean getIsPiercing() {
        return isPiercing;
    }
}
