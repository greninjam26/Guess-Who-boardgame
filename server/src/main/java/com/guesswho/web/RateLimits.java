package com.guesswho.web;

import java.time.Duration;

/**
 * What each kind of request is allowed, and what is deliberately not limited.
 *
 * <p>Gathered in one place because the numbers are only defensible next to each
 * other: what matters is that signing in is held far tighter than playing, and
 * that is invisible when the figures live in the controllers they apply to.</p>
 */
final class RateLimits {
    /**
     * Signing in, per address.
     *
     * <p>The one that matters most, and the one the roadmap did not list. It is
     * the only unauthenticated endpoint that takes a password, so it is the only
     * one somebody can guess at — and every attempt costs a BCrypt hash, which
     * is expensive on purpose. That makes an unlimited login endpoint both a way
     * to guess passwords and a way to burn the server's CPU with a few hundred
     * requests.</p>
     *
     * <p>Keyed by address rather than by username. Limiting per username lets
     * anybody lock a player out of their own account by failing to log in as
     * them, which turns a defence into a weapon.</p>
     */
    static final RateLimiter.Policy SIGN_IN =
            new RateLimiter.Policy(10, Duration.ofMinutes(1));

    /**
     * Creating accounts, per address.
     *
     * <p>Rows in a table that nothing expires, from a caller who has not proved
     * anything about themselves yet.</p>
     */
    static final RateLimiter.Policy REGISTER =
            new RateLimiter.Policy(5, Duration.ofMinutes(10));

    /**
     * Opening rooms, per account.
     *
     * <p>The cap of five open rooms already bounds how many exist at once; this
     * bounds the churn of opening and abandoning them, which the cap does not
     * see because an abandoned room stops counting as soon as it expires.</p>
     */
    static final RateLimiter.Policy OPEN_ROOM =
            new RateLimiter.Policy(10, Duration.ofMinutes(10));

    /**
     * Playing, per account.
     *
     * <p>Generous to the point of being invisible to a real game, where a move
     * takes a person several seconds to decide. It is here to bound a client
     * that has gone wrong rather than to pace anybody: the rules already refuse
     * a move out of turn, so the damage a fast player can do is limited before
     * this is reached.</p>
     */
    static final RateLimiter.Policy MOVE =
            new RateLimiter.Policy(60, Duration.ofMinutes(1));

    private RateLimits() {
    }

    // Reading the game — GET /api/rooms/{code}/state — is deliberately not
    // limited, and it is the busiest endpoint by a wide margin.
    //
    // It is the client's heartbeat. Presence is measured by requests, so a poll
    // that comes back 429 is a poll that did not mark the player present — and
    // a player who is throttled for long enough stops looking present, then
    // stops looking present for long enough to lose the game. A limit here would
    // forfeit games as a side effect of protecting the server, which is a worse
    // outcome than the load it saves.
    //
    // The load is bounded anyway: polling only happens inside a room, a room
    // holds two accounts, both are signed in, and rooms expire. The way to slow
    // it is the poll interval, which the client already sets and the server can
    // one day send.
}
