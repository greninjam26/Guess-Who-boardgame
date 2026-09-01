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

        assertEquals(2000, seen.size(), "Two rooms answering to one code loses a game");
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
