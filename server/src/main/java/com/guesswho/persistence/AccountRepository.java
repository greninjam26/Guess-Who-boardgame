package com.guesswho.persistence;

import com.guesswho.account.Account;
import java.util.Optional;

/**
 * Stores registered players.
 *
 * <p>Server-side only, unlike the leaderboard's repository: nothing outside the
 * server has any business reading a password hash, and an interface the client
 * module could see is one it could implement.</p>
 */
public interface AccountRepository {
    /**
     * Registers a new player.
     *
     * @param username     the name as they typed it
     * @param passwordHash the already-hashed password; this never sees a plain one
     * @return the account, without the hash
     * @throws UsernameTakenException if the name is already registered, ignoring case
     */
    Account create(String username, String passwordHash);

    /**
     * Finds an account by name, ignoring case, with its hash for checking a
     * password against.
     *
     * @param username the name typed at a login prompt
     * @return the stored account, or empty when no such player exists
     */
    Optional<StoredAccount> findByUsername(String username);

    /**
     * Whether a name is already registered, ignoring case.
     *
     * @param username the name to check
     * @return true when somebody already has it
     */
    boolean exists(String username);

    /**
     * An account together with the hash to check a password against.
     *
     * <p>Separate from {@link Account} so that the hash cannot leave the server
     * by accident: the type that carries it is not the type that gets returned
     * from a controller.</p>
     *
     * @param account      the player
     * @param passwordHash their stored password hash
     */
    record StoredAccount(Account account, String passwordHash) {
    }

    /** Thrown when a username is already taken. */
    class UsernameTakenException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * @param username the name somebody already has
         */
        public UsernameTakenException(String username) {
            super("Username is already taken: " + username);
        }
    }
}
