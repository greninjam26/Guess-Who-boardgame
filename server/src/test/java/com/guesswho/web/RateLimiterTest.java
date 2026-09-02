package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
    private final RateLimiter limiter = new RateLimiter(true);

    @Test
    void allowsTheBurstAndThenRefuses() {
        RateLimiter.Policy three = new RateLimiter.Policy(3, Duration.ofMinutes(1));

        assertTrue(limiter.allow("somebody", three));
        assertTrue(limiter.allow("somebody", three));
        assertTrue(limiter.allow("somebody", three));
        assertFalse(limiter.allow("somebody", three), "A fourth should be refused");
    }

    @Test
    void keepsCallersApart() {
        //One player exhausting their allowance must not lock anybody else out,
        //which is the failure that turns a defence into a way of attacking.
        RateLimiter.Policy one = new RateLimiter.Policy(1, Duration.ofMinutes(1));
        limiter.allow("first", one);

        assertTrue(limiter.allow("second", one));
    }

    @Test
    void refillsAsTimePasses() {
        //A whole allowance per millisecond, so waiting a moment is enough to
        //show it coming back rather than making the test sleep for a minute.
        RateLimiter.Policy fast = new RateLimiter.Policy(1, Duration.ofMillis(1));
        assertTrue(limiter.allow("somebody", fast));

        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        boolean allowedAgain = false;
        while (System.nanoTime() < deadline && !allowedAgain) {
            allowedAgain = limiter.allow("somebody", fast);
        }

        assertTrue(allowedAgain, "An allowance that never comes back is a ban");
    }

    @Test
    void staysBoundedWhileBeingHammeredByManyCallers() {
        //The limiter must not become the thing it protects against. A row per
        //caller, kept for ever, is a memory leak anybody who can send requests
        //can reach.
        RateLimiter.Policy fast = new RateLimiter.Policy(1, Duration.ofMillis(1));

        for (int caller = 0; caller < 40_000; caller++) {
            limiter.allow("caller-" + caller, fast);
        }

        //Still answering, and still enforcing, having seen four times what it
        //tracks at once.
        RateLimiter.Policy one = new RateLimiter.Policy(1, Duration.ofHours(1));
        assertTrue(limiter.allow("someone-new", one));
        assertFalse(limiter.allow("someone-new", one),
                "Forgetting idle callers must not forget the ones still spending");
    }

    @Test
    void signingInIsHeldFarTighterThanPlaying() {
        //The relationship is the point, not the numbers. Guessing at a password
        //has to be much harder than taking a turn, and these are the two
        //allowances most likely to be adjusted without looking at each other.
        assertTrue(perMinute(RateLimits.SIGN_IN) * 4 < perMinute(RateLimits.MOVE),
                "A login allowance close to the move allowance is not a defence");
    }

    private static double perMinute(RateLimiter.Policy policy) {
        return policy.burst() / (double) policy.per().toMinutes();
    }
}
