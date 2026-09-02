package com.guesswho.api;

/**
 * The version of the wire protocol a client speaks.
 *
 * <p>In {@code game-core} so that both sides read the same number from the same
 * place. A version the client and the server each defined for themselves would
 * agree right up until somebody changed one of them.</p>
 *
 * <p>A header rather than a path. There is one client and it ships from this
 * repository, so {@code /api/v1/} would mean rewriting every URL and every call
 * to buy a property nobody outside is relying on. The header leaves the URLs
 * alone and says the same thing.</p>
 */
public final class ApiVersion {
    /** What a client puts its version in. */
    public static final String HEADER = "X-Api-Version";

    /**
     * What this build speaks.
     *
     * <p>Raise it when a change would make an older client behave wrongly
     * rather than merely miss out — a field it needs disappearing, a status code
     * meaning something new, a move it would send the old shape of. Adding an
     * endpoint or a field is not that: a client that ignores something new is
     * fine.</p>
     */
    public static final int CURRENT = 1;

    /**
     * The oldest version the server will still answer.
     *
     * <p>Zero, deliberately, and it should stay there until a change actually
     * breaks something. Zero means "the builds from before there was a version
     * header", so every installer already on somebody's disk keeps working
     * against the first deployed server.</p>
     *
     * <p>Rejecting them on day one would be the version check doing harm rather
     * than good: nothing is incompatible yet, so the only thing it would achieve
     * is locking out the exact generation of clients that cannot understand the
     * rejection. Those builds map any status they do not recognise to "the
     * server could not be reached", and since reconnect landed that means a
     * banner and a retry every two seconds — a player told to wait for ever
     * instead of to update.</p>
     *
     * <p>So the mechanism ships now and does nothing. By the time raising this
     * matters, the clients in the wild send a version and know what a 426
     * means.</p>
     */
    public static final int MINIMUM_SUPPORTED = 0;

    private ApiVersion() {
    }

    /**
     * Reads the version a client claims.
     *
     * @param header the header's value, or null when it sent none
     * @return the version claimed, or 0 for a client from before there was one
     */
    public static int claimedBy(String header) {
        if (header == null || header.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(header.trim());
        }
        catch (NumberFormatException notANumber) {
            //Unreadable is treated as oldest rather than refused outright. It
            //is the same situation as no header at all — a client this server
            //does not recognise — and the two deserve the same answer.
            return 0;
        }
    }

    /**
     * Whether this server will answer a client speaking that version.
     *
     * @param claimed the version the client claims
     * @return true when the server should serve it
     */
    public static boolean isSupported(int claimed) {
        return claimed >= MINIMUM_SUPPORTED;
    }
}
