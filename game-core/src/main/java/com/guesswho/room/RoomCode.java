package com.guesswho.room;

import java.security.SecureRandom;

/**
 * The six characters somebody reads out to a friend.
 *
 * <p>The alphabet leaves out everything that gets misheard or mistyped from
 * another screen: no {@code O} against {@code 0}, no {@code I} or {@code 1}
 * against {@code L}, and no vowels, which keeps real words from appearing in a
 * code people will read aloud.</p>
 *
 * <p>That leaves 23 characters and 23^6 codes — around 148 million. Guessing at
 * one is not a way into somebody's game, and the codes are short enough to say
 * over the phone.</p>
 */
public final class RoomCode {
    private static final char[] ALPHABET = "BCDFGHJKMNPQRSTVWXYZ2346789".toCharArray();
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RoomCode() {
    }

    /**
     * Makes a new code.
     *
     * @return six characters from the unambiguous alphabet
     */
    public static String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int character = 0; character < LENGTH; character++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * Tidies a code somebody typed into the form codes are stored in.
     *
     * <p>People type them in lower case and with spaces in the middle. Refusing
     * those would be pedantry about a code that is otherwise correct.</p>
     *
     * @param typed what they entered
     * @return the tidied code, or null when it could not be one
     */
    public static String normalise(String typed) {
        if (typed == null) {
            return null;
        }
        String tidied = typed.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        return isValid(tidied) ? tidied : null;
    }

    /**
     * Whether a string could be a code at all.
     *
     * <p>Worth checking before a database lookup: a code of the wrong shape is
     * a typo, and answering it without a query keeps guessing cheap for nobody.</p>
     *
     * @param code the code to check
     * @return true when it is the right length and alphabet
     */
    public static boolean isValid(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        return code.chars().allMatch(character ->
                new String(ALPHABET).indexOf(character) >= 0);
    }
}
