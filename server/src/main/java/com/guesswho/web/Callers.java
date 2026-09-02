package com.guesswho.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Works out who to hold a limit against, and refuses them when they exceed it.
 *
 * <p>Two kinds of caller. Somebody who has signed in is their account, which is
 * the honest answer: it follows them between networks and cannot be swapped by
 * changing address. Somebody who has not is their address, because it is the
 * only thing there is — which is exactly why the endpoints that accept a
 * password are the ones held tightest.</p>
 */
final class Callers {
    private Callers() {
    }

    /**
     * Refuses a caller who has spent their allowance for this kind of request.
     *
     * @param limiter what is counting
     * @param action  which allowance, so one kind of request cannot spend
     *                another's
     * @param from    the request, for the caller's address
     * @param policy  how much they are allowed
     * @throws ResponseStatusException 429 when they have had enough
     */
    static void require(RateLimiter limiter, String action, HttpServletRequest from,
            RateLimiter.Policy policy) {
        require(limiter, action + ":" + addressOf(from), policy);
    }

    /**
     * Refuses a signed-in caller who has spent their allowance.
     *
     * @param limiter   what is counting
     * @param action    which allowance
     * @param accountId whose allowance
     * @param policy    how much they are allowed
     * @throws ResponseStatusException 429 when they have had enough
     */
    static void require(RateLimiter limiter, String action, long accountId,
            RateLimiter.Policy policy) {
        require(limiter, action + ":account:" + accountId, policy);
    }

    private static void require(RateLimiter limiter, String key, RateLimiter.Policy policy) {
        if (!limiter.allow(key, policy)) {
            //429 rather than 403: the request was allowed, there have just been
            //too many of them, and waiting is the remedy. Saying so is what
            //lets a client behave rather than guess.
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Wait a moment and try again.");
        }
    }

    /**
     * The address a request came from.
     *
     * <p>Deliberately not reading {@code X-Forwarded-For}. Behind nothing, that
     * header is whatever the caller typed, so trusting it would let anybody
     * spend somebody else's allowance — or dodge their own by inventing a new
     * address per request, which turns the limit off. When this is deployed
     * behind a proxy that sets it, the proxy's own configuration is what should
     * make it trustworthy, and that is Phase 10's business rather than a guess
     * made here.</p>
     */
    private static String addressOf(HttpServletRequest from) {
        String address = from == null ? null : from.getRemoteAddr();
        return address == null ? "unknown" : address;
    }
}
