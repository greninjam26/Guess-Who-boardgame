package com.guesswho.client;

/**
 * What came of talking to the server about an online game.
 *
 * <p>A value rather than an exception, for the same reason signing in returns
 * one: each of these is a different sentence a screen has to say, and none of
 * them is a fault in the program. A room that is full, a code that opens
 * nothing, and a server that cannot be reached all need different words.</p>
 *
 * @param kind    what happened
 * @param value   what came back, when something did
 * @param message what to tell the player, when there is something to say
 * @param <T>     the kind of thing the call returns
 */
public record OnlineOutcome<T>(OnlineOutcome.Kind kind, T value, String message) {
    /** The kinds of thing that can come of an online request. */
    public enum Kind {
        /** It worked. */
        OK,
        /** The rules said no: out of turn, already chosen, and so on. */
        REFUSED,
        /** No such game, or not one this player is in. */
        NOT_FOUND,
        /** This account already has as many games open as it may. */
        TOO_MANY_ROOMS,
        /** The session is no longer good; the player needs to sign in again. */
        SIGNED_OUT,
        /** The server could not be reached, which is not the player's fault. */
        UNREACHABLE,
        /**
         * This build is too old for the server, and no amount of retrying fixes
         * it.
         *
         * <p>Its own kind rather than a refusal, because the remedy is different
         * from every other failure here: not waiting, not signing in again, but
         * downloading a new build. Folding it into UNREACHABLE — which is what a
         * client that does not know this status does — leaves a player watching
         * a reconnecting banner for ever.</p>
         */
        OUTDATED
    }

    /**
     * @param value what came back
     * @param <T>   the kind of thing the call returns
     * @return a successful outcome
     */
    public static <T> OnlineOutcome<T> ok(T value) {
        return new OnlineOutcome<>(Kind.OK, value, null);
    }

    /**
     * @param kind    what went wrong
     * @param message what to tell the player
     * @param <T>     the kind of thing the call would have returned
     * @return an unsuccessful outcome
     */
    public static <T> OnlineOutcome<T> failed(Kind kind, String message) {
        return new OnlineOutcome<>(kind, null, message);
    }

    /** Whether the call did what was asked. */
    public boolean isOk() {
        return kind == Kind.OK;
    }
}
