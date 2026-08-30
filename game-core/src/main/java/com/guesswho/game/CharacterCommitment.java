package com.guesswho.game;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A promise about which character was chosen, made before play and checked
 * after it.
 *
 * <p>Locally this is not needed: the game holds both characters, so a choice is
 * made final simply by refusing to change it. It matters when someone has to
 * verify a game they were never told the answer to — an online opponent's
 * questions are answered by their own client, so the server can record a whole
 * game without ever learning either character, and cannot leak what it does not
 * hold.</p>
 *
 * <p>The hash covers the character together with a random nonce. Without the
 * nonce, twenty-four characters is a small enough set to try every one and
 * recognise the answer.</p>
 *
 * <p>This proves a character was not swapped after the questions began. It does
 * not defend against a modified client, which could commit to one character and
 * answer as though it held another — that is a different problem and this does
 * not pretend to solve it.</p>
 *
 * @param hash hex-encoded SHA-256 of the character name and nonce
 * @param nonce hex-encoded random value, withheld until the reveal
 */
public record CharacterCommitment(String hash, String nonce) {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 16;

    /**
     * Creates a commitment with an immutable copy of its values.
     */
    public CharacterCommitment {
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(nonce, "nonce");
    }

    /**
     * Commits to a character, generating a fresh nonce.
     *
     * @param characterName the chosen character
     * @return a commitment that will later verify against that character
     */
    public static CharacterCommitment to(String characterName) {
        Objects.requireNonNull(characterName, "characterName");
        byte[] nonceBytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        return new CharacterCommitment(digest(characterName, nonce), nonce);
    }

    /**
     * Checks a revealed character against this commitment.
     *
     * @param characterName the character being revealed
     * @return {@code true} when the reveal matches what was committed to
     */
    public boolean matches(String characterName) {
        if (characterName == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash.getBytes(StandardCharsets.UTF_8),
                digest(characterName, nonce).getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(String characterName, String nonce) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(characterName.getBytes(StandardCharsets.UTF_8));
            //separated so that "Sam" + "12ab" cannot collide with "Sam1" + "2ab"
            sha256.update((byte) 0);
            sha256.update(nonce.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha256.digest());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required and always present", exception);
        }
    }
}
