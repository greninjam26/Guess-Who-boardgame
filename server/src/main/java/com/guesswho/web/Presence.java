package com.guesswho.web;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether a player is still there.
 *
 * <p>Two questions, not one, and they want different answers. Telling a player
 * their opponent seems to have gone is a hint: it costs nothing if it is wrong,
 * it corrects itself on the next poll, and it is worth saying quickly. Taking
 * somebody's game away is not: it is irreversible, it happens to the player who
 * is not looking, and being wrong once means a real game lost to a bad minute
 * of wifi.</p>
 *
 * <p>So the hint is fast and the forfeit is slow, and the gap between them is
 * deliberate. A player can be shown as gone while still being safe from
 * forfeit, which is exactly the state somebody on a flaky connection is in.</p>
 *
 * <p>The moment to judge against is always passed in. Reading the clock in here
 * would make every caller's tests depend on how fast they ran.</p>
 */
final class Presence {
    /**
     * How long since a player was heard from before they are shown as gone.
     *
     * <p>Clients poll every two seconds, so this is about seven missed polls —
     * long enough not to flicker on a slow network, short enough that the
     * person waiting finds out while they still care.</p>
     */
    static final Duration PRESENT_WITHIN = Duration.ofSeconds(15);

    /**
     * How long a player must be silent before their game can be forfeited.
     *
     * <p>Six presence windows rather than one, and written as a multiple to say
     * why: one lapse must not be able to end a game. At a two-second poll this
     * is around forty-five consecutive failures — an outage, not a hiccup — and
     * it is only ever reached by a player who has also not moved for the whole
     * turn limit. Somebody who steps away and comes back inside a minute and a
     * half keeps the game they were playing.</p>
     *
     * <p>Erring long is the cheap direction. Forfeiting late costs the waiting
     * player a little more waiting, with the room's own expiry as the backstop;
     * forfeiting early costs somebody a game they were in the middle of.</p>
     */
    static final Duration SILENT_BEFORE_FORFEIT = PRESENT_WITHIN.multipliedBy(6);

    private Presence() {
    }

    /**
     * Whether somebody last heard from at this moment still counts as present.
     *
     * <p>For what a player is told about their opponent. Not the question to ask
     * before forfeiting — see {@link #hasAbandoned}.</p>
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

    /**
     * Whether somebody has been silent long enough to lose the game for it.
     *
     * <p>Not the opposite of {@link #isPresent}. Between the two thresholds a
     * player is shown as gone and is still safe, which is where a bad
     * connection puts somebody who is sitting right there.</p>
     *
     * @param lastSeen when they were last heard from, or null if never
     * @param now      the moment to judge against
     * @return true when the silence is long enough to act on
     */
    static boolean hasAbandoned(Instant lastSeen, Instant now) {
        if (lastSeen == null) {
            //Rooms opened before presence was recorded have no sighting at all.
            //Reading that as abandonment would forfeit a game on the strength of
            //a column that did not exist when it started, so these are left to
            //the room's own expiry instead.
            return false;
        }
        return !lastSeen.isAfter(now.minus(SILENT_BEFORE_FORFEIT));
    }
}
