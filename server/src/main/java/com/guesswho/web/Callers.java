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
     * <p>This reads {@code getRemoteAddr()} and nothing else, which is correct
     * in both deployments — but it means different things in each, and the
     * difference is configuration rather than code.</p>
     *
     * <p>Run directly, {@code getRemoteAddr()} is the caller's own address and
     * {@code X-Forwarded-For} is whatever they typed, so nothing here reads it.
     * Behind the deployed proxy, {@code server.forward-headers-strategy=FRAMEWORK}
     * in {@code application-aws.properties} puts Spring's forwarded-header
     * filter in front of this, and {@code getRemoteAddr()} becomes the address
     * the proxy reports. That is the whole change: this method never learns
     * which world it is in.</p>
     *
     * <p>Phase 10 settled the question this used to defer, and the answer has
     * two halves that are only safe together. Spring may trust the header
     * because the application binds to loopback and only Caddy can reach it —
     * <em>and</em> because the Caddyfile strips {@code Forwarded} and
     * <em>replaces</em> {@code X-Forwarded-For} rather than appending to it.
     * Caddy appends by default, and the framework reads the left-most entry, so
     * an appending proxy would let any caller name themselves whatever they
     * liked and dodge the limit entirely. {@code ForwardedAddressTest} covers
     * the framework half; a curl against real Caddy covers the other.</p>
     */
    private static String addressOf(HttpServletRequest from) {
        String address = from == null ? null : from.getRemoteAddr();
        return address == null ? "unknown" : address;
    }
}
