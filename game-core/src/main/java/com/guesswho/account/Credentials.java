package com.guesswho.account;

/**
 * A username and password, on their way to being checked or stored.
 *
 * <p>Only ever holds a password in transit. Nothing keeps one of these.</p>
 *
 * @param username the name the player typed
 * @param password the password they typed
 */
public record Credentials(String username, String password) {
}
