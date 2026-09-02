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
    void oneLapseShowsThemGoneWithoutForfeitingTheirGame() {
        //The whole reason there are two thresholds. A player whose connection
        //drops for half a minute is reported as gone — which is honest, their
        //client has stopped answering — and keeps the game they are sitting in
        //front of.
        Instant lapsed = NOW.minusSeconds(30);

        assertFalse(Presence.isPresent(lapsed, NOW), "Their client has stopped answering");
        assertFalse(Presence.hasAbandoned(lapsed, NOW),
                "One bad half-minute must not end a game somebody is playing");
    }

    @Test
    void forfeitNeedsSeveralWindowsOfSilenceRatherThanOne() {
        //Written as a multiple in Presence, so this checks the relationship
        //rather than the number: whatever the display window becomes, the
        //forfeit threshold has to stay several of them away from it.
        assertFalse(Presence.hasAbandoned(NOW.minus(Presence.PRESENT_WITHIN), NOW),
                "Silence just past the display window is not grounds to forfeit");
        assertTrue(Presence.SILENT_BEFORE_FORFEIT.compareTo(
                        Presence.PRESENT_WITHIN.multipliedBy(2)) > 0,
                "A forfeit threshold this close to the display window is one lapse away");
    }

    @Test
    void sustainedSilenceIsAbandonment() {
        assertTrue(Presence.hasAbandoned(
                NOW.minus(Presence.SILENT_BEFORE_FORFEIT).minusSeconds(1), NOW));
    }

    @Test
    void silenceExactlyAtTheForfeitThresholdCounts() {
        //The boundary belongs to abandonment, matching how the display window
        //treats its own edge.
        assertTrue(Presence.hasAbandoned(NOW.minus(Presence.SILENT_BEFORE_FORFEIT), NOW));
    }

    @Test
    void aRoomWithNoSightingIsLeftToExpireRatherThanForfeited() {
        //Rooms opened before presence was recorded have a null last_seen.
        //Reading that as abandonment would end a game on the strength of a
        //column that did not exist when it started.
        assertFalse(Presence.hasAbandoned(null, NOW));
    }

    @Test
    void somebodyNeverHeardFromHasNotArrived() {
        //Rooms opened before presence was recorded have no sighting at all, and
        //a null must not read as "here" for want of a timestamp to compare.
        assertFalse(Presence.isPresent(null, NOW));
    }
}
