package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The one rule that decides whether somebody is still there.
 *
 * <p>Tested against a moment that is passed in rather than read, so these say
 * the same thing however fast the machine running them happens to be.</p>
 */
class PresenceTest {
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void somebodyHeardFromAMomentAgoIsPresent() {
        assertTrue(Presence.isPresent(NOW.minusSeconds(2), NOW));
    }

    @Test
    void somebodyHeardFromRightOnTheEdgeIsStillPresent() {
        assertTrue(Presence.isPresent(
                NOW.minus(Presence.PRESENT_WITHIN).plusMillis(1), NOW));
    }

    @Test
    void somebodyHeardFromExactlyAtTheEdgeHasGone() {
        //The boundary belongs to absence, so the window is a duration a player
        //has rather than one they have plus an instant.
        assertFalse(Presence.isPresent(NOW.minus(Presence.PRESENT_WITHIN), NOW));
    }

    @Test
    void somebodyHeardFromAMinuteAgoHasGone() {
        assertFalse(Presence.isPresent(NOW.minusSeconds(60), NOW));
    }

    @Test
    void somebodyNeverHeardFromHasNotArrived() {
        //Rooms opened before presence was recorded have no sighting at all, and
        //a null must not read as "here" for want of a timestamp to compare.
        assertFalse(Presence.isPresent(null, NOW));
    }
}
