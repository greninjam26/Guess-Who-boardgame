package com.guesswho.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomCodeTest {
    @Test
    void makesASixCharacterCode() {
        assertEquals(6, RoomCode.next().length());
    }

    @Test
    void leavesOutTheCharactersPeopleMishear() {
        //A code is read off one screen and typed into another, so O against 0
        //and I against 1 against L are all a lost game.
        String forbidden = "OIL01AEU";
        for (int attempt = 0; attempt < 500; attempt++) {
            for (char character : RoomCode.next().toCharArray()) {
                assertFalse(forbidden.indexOf(character) >= 0,
                        "Code contained an easily confused character: " + character);
            }
        }
    }

    @Test
    void doesNotRepeatItselfInAnyReasonableNumberOfRooms() {
        Set<String> seen = new HashSet<>();
        for (int attempt = 0; attempt < 2000; attempt++) {
            seen.add(RoomCode.next());
        }

        //Not 2000 exactly. Two thousand draws from 23^6 collide about one run
        //in seventy-five by pure chance, and an earlier version of this test
        //asserted perfection and failed on it. What is worth catching is a
        //generator that repeats systematically — one using a fraction of its
        //alphabet would come nowhere near this.
        assertTrue(seen.size() >= 1995,
                "Codes are repeating far more than chance explains: " + seen.size()
                        + " unique out of 2000");
    }

    @Test
    void usesTheWholeAlphabet() {
        //The property the uniqueness test is really reaching for: a generator
        //stuck on a few characters is the way codes start colliding.
        Set<Character> used = new HashSet<>();
        for (int attempt = 0; attempt < 2000; attempt++) {
            for (char character : RoomCode.next().toCharArray()) {
                used.add(character);
            }
        }

        assertEquals(27, used.size(), "Some characters are never produced: " + used);
    }

    @Test
    void acceptsACodeTypedTheWayPeopleTypeThem() {
        String code = RoomCode.next();

        assertEquals(code, RoomCode.normalise(code.toLowerCase(Locale.ROOT)));
        assertEquals(code, RoomCode.normalise("  " + code + " "));
        assertEquals(code, RoomCode.normalise(code.substring(0, 3) + " " + code.substring(3)));
        assertEquals(code, RoomCode.normalise(code.substring(0, 3) + "-" + code.substring(3)));
    }

    @Test
    void refusesSomethingThatCouldNotBeACode() {
        assertNull(RoomCode.normalise(null));
        assertNull(RoomCode.normalise(""));
        assertNull(RoomCode.normalise("SHORT"));
        assertNull(RoomCode.normalise("TOOLONGG"));
        //Characters the alphabet deliberately excludes.
        assertNull(RoomCode.normalise("AEIOU1"));
        assertNull(RoomCode.normalise("BCDFG;"));
    }

    @Test
    void everyGeneratedCodeIsOneItWouldAccept() {
        for (int attempt = 0; attempt < 500; attempt++) {
            String code = RoomCode.next();
            assertTrue(RoomCode.isValid(code), code);
            assertEquals(code, RoomCode.normalise(code));
        }
    }
}
