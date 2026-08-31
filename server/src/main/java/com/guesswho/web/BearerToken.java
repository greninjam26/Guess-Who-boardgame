package com.guesswho.web;

/**
 * Pulls the token out of an {@code Authorization} header.
 */
final class BearerToken {
    private static final String PREFIX = "Bearer ";

    private BearerToken() {
    }

    /**
     * @param authorization the header value, or null when absent
     * @return the token, or null when the header is missing or not a bearer one
     */
    static String from(String authorization) {
        if (authorization == null || !authorization.regionMatches(
                true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
