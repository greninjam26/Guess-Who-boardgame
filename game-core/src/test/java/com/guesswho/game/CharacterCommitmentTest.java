package com.guesswho.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CharacterCommitmentTest {
    @Test
    void acceptsTheCharacterItWasMadeFor() {
        CharacterCommitment commitment = CharacterCommitment.to("Sam");

        assertTrue(commitment.matches("Sam"));
    }

    @Test
    void rejectsADifferentCharacter() {
        CharacterCommitment commitment = CharacterCommitment.to("Sam");

        assertFalse(commitment.matches("Olivia"),
                "Swapping a character after the questions began is the cheat this catches");
    }

    @Test
    void rejectsAMissingReveal() {
        assertFalse(CharacterCommitment.to("Sam").matches(null));
    }

    @Test
    void hidesWhichCharacterWasChosen() {
        CharacterCommitment commitment = CharacterCommitment.to("Sam");

        assertFalse(commitment.hash().contains("Sam"),
                "The hash travels while the game is in progress and must not reveal the answer");
    }

    @Test
    void differsBetweenCommitmentsToTheSameCharacter() {
        CharacterCommitment first = CharacterCommitment.to("Sam");
        CharacterCommitment second = CharacterCommitment.to("Sam");

        assertNotEquals(first.hash(), second.hash(),
                "A fixed hash for a character would be recognisable across games");
        assertNotEquals(first.nonce(), second.nonce());
    }

    @Test
    void verifiesFromTheHashAndNonceAlone() {
        CharacterCommitment original = CharacterCommitment.to("Nick");

        CharacterCommitment received = new CharacterCommitment(
                original.hash(), original.nonce());

        assertTrue(received.matches("Nick"),
                "A verifier reconstructs it from what was sent, not from the original object");
    }

    @Test
    void separatesTheCharacterFromTheNonce() {
        CharacterCommitment commitment = new CharacterCommitment(
                CharacterCommitment.to("Sam").hash(), "");

        assertFalse(commitment.matches("Sam"),
                "Concatenating without a separator would let a name and nonce shift between them");
    }

    @Test
    void refusesToCommitToNothing() {
        assertThrows(NullPointerException.class, () -> CharacterCommitment.to(null));
    }

    @Test
    void producesAFullLengthHash() {
        assertEquals(64, CharacterCommitment.to("Sam").hash().length(),
                "SHA-256 is 32 bytes, so 64 hex characters");
    }
}
