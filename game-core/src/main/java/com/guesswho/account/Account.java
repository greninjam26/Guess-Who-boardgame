package com.guesswho.account;

/**
 * A registered player, as everyone other than the server sees them.
 *
 * <p>Deliberately without the password hash. This is what a login returns and
 * what a leaderboard row will eventually point at, and the hash has no business
 * in either — a record that can carry it is a record that will one day be
 * serialised with it.</p>
 *
 * @param id       the account's identifier, which outlives any name change
 * @param username the name as the player typed it
 */
public record Account(long id, String username) {
}
