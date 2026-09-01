package com.guesswho.web;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether a player is still there.
 *
 * <p>One rule in one place, because two things depend on it and they must not
 * disagree: what a player is told about their opponent, and whether a turn that
 * has run out forfeits the game. If the projection said somebody was present
 * while the forfeit rule said they were gone, a player would watch the game be
 * taken off them by a server that had just told them their opponent was
 * there.</p>
 *
 * <p>The moment to judge against is always passed in. Reading the clock in here
 * would make every caller's tests depend on how fast they ran.</p>
 */
final class Presence {
    /**
     * How long since a player was heard from before they count as gone.
     *
     * <p>Clients poll every two seconds, so anything past a few missed polls
     * means their game is not open any more. Long enough not to flicker on a
     * slow network; short enough that the person waiting finds out while they
     * still care.</p>
     */
    static final Duration PRESENT_WITHIN = Duration.ofSeconds(15);

    private Presence() {
    }

    /**
     * Whether somebody last heard from at this moment still counts as present.
     *
     * @param lastSeen when they were last heard from, or null if never
     * @param now      the moment to judge against
     * @return true when they are still considered there
     */
    static boolean isPresent(Instant lastSeen, Instant now) {
        //Never heard from counts as absent: a player who has not managed a
        //single request has not arrived.
        return lastSeen != null && lastSeen.isAfter(now.minus(PRESENT_WITHIN));
    }
}
