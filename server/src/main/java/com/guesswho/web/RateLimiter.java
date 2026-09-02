package com.guesswho.web;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * How often one caller may do one kind of thing.
 *
 * <p>In memory, and deliberately. This is one Spring Boot instance by design,
 * so a shared store would be infrastructure bought to solve a problem the
 * deployment does not have. If the server ever runs as more than one instance
 * the limits become per-instance, which is a weakening rather than a break —
 * and by then Phase 13's entry condition applies: measure first.</p>
 *
 * <p>A token bucket rather than a fixed window. A window lets somebody spend a
 * whole allowance at the end of one and another at the start of the next, which
 * for a login limit is twice the guesses in a moment.</p>
 *
 * <p>The map is capped. A limiter that grows a row per attacker is a memory
 * leak reachable by anybody who can send requests — the wrong shape for a thing
 * whose job is bounding abuse.</p>
 */
@Component
class RateLimiter {
    /**
     * How many callers are tracked at once.
     *
     * <p>Far above any real player count. When it is reached, buckets that are
     * full are dropped: a full bucket is one nobody has spent, so forgetting it
     * loses nothing an attacker could exploit.</p>
     */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;

    /**
     * @param enabled whether limits are enforced. Off in the test suite, which
     *                registers hundreds of accounts from one address and would
     *                otherwise spend a real allowance in its first few classes —
     *                the same reason the room sweep has a switch. The limits
     *                themselves are covered by their own tests, and by one that
     *                turns this back on and checks a real endpoint answers 429.
     */
    RateLimiter(@org.springframework.beans.factory.annotation.Value(
            "${guesswho.rate-limits.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Whether this caller may act, spending one of their allowance if so.
     *
     * @param key    who is asking, scoped to what they are asking to do
     * @param policy how much they are allowed
     * @return true when the request may proceed
     */
    boolean allow(String key, Policy policy) {
        if (!enabled) {
            return true;
        }
        if (buckets.size() >= MAX_TRACKED) {
            forgetIdleCallers();
        }
        //The policy belongs to the bucket, because a key is already scoped to
        //one kind of request — "login:1.2.3.4" is only ever asked about with the
        //login allowance.
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(policy));
        return bucket.spend();
    }

    /** Drops the buckets nobody is currently spending. */
    private void forgetIdleCallers() {
        buckets.values().removeIf(Bucket::isFull);
    }

    /** Empties everything, so one test's attempts are not another's. */
    void reset() {
        buckets.clear();
    }

    /**
     * An allowance: how many actions, and how long a full bucket takes to
     * refill from empty.
     *
     * @param burst how many are allowed at once
     * @param per   how long refilling the whole allowance takes
     */
    record Policy(int burst, Duration per) {
        /** @return how long one token takes to come back, in nanoseconds */
        long refillNanos() {
            return per.toNanos() / burst;
        }
    }

    /** One caller's remaining allowance for one kind of request. */
    private static final class Bucket {
        private final Policy policy;
        private double tokens;
        private long refilledAtNanos;

        private Bucket(Policy policy) {
            this.policy = policy;
            this.tokens = policy.burst();
            this.refilledAtNanos = System.nanoTime();
        }

        private synchronized boolean spend() {
            refill();
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        /**
         * Whether this caller has their whole allowance back.
         *
         * <p>Refills first, which is the point of it: eviction runs over buckets
         * nobody has touched, and a bucket that is only counted when spent from
         * would look empty for ever and never be forgotten. That would turn the
         * cap into a wall the first attacker reaches and never leaves.</p>
         */
        private synchronized boolean isFull() {
            refill();
            return tokens >= policy.burst();
        }

        /**
         * Adds whatever the elapsed time is worth.
         *
         * <p>Worked out from the clock rather than on a schedule: a timer per
         * caller would cost more than the thing it is protecting.</p>
         */
        private void refill() {
            long now = System.nanoTime();
            tokens = Math.min(policy.burst(),
                    tokens + (now - refilledAtNanos) / (double) policy.refillNanos());
            refilledAtNanos = now;
        }
    }
}
