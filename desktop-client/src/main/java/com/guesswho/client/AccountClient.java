package com.guesswho.client;

import com.guesswho.account.Account;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Registering, logging in and logging out, from the game's side.
 */
public interface AccountClient {
    /**
     * Registers a new player.
     *
     * @param username the name they chose
     * @param password the password they chose
     * @return what happened
     */
    CompletableFuture<Outcome> register(String username, String password);

    /**
     * Logs in.
     *
     * @param username the name they typed
     * @param password the password they typed
     * @return what happened, including the token when it worked
     */
    CompletableFuture<Outcome> logIn(String username, String password);

    /**
     * Checks a stored token still means something.
     *
     * @param token a token from a previous session
     * @return the account it belongs to, or empty when it is no longer good
     */
    CompletableFuture<Optional<Account>> whoAmI(String token);

    /**
     * Ends the session on the server.
     *
     * @param token the token being given up
     * @return completes when the server has been told, or has failed to be
     */
    CompletableFuture<Void> logOut(String token);

    /**
     * What came of an attempt to register or log in.
     *
     * <p>A value rather than an exception, because each of these is a different
     * sentence the login screen has to say, and none of them is a fault in the
     * program.</p>
     *
     * @param kind    what happened
     * @param token   the session token, when logging in worked
     * @param account who logged in or registered, when it worked
     * @param message what to tell the player, when the server explained itself
     */
    record Outcome(Kind kind, String token, Account account, String message) {
        /** The kinds of thing that can come of trying to log in or register. */
        public enum Kind {
            /** Logged in; there is a token. */
            LOGGED_IN,
            /** Registered, but not yet logged in. */
            REGISTERED,
            /** The name or the password was wrong; deliberately not saying which. */
            WRONG_CREDENTIALS,
            /** Somebody already has that name. */
            USERNAME_TAKEN,
            /** The server refused it and said why. */
            REJECTED,
            /** The server could not be reached, which is not the player's fault. */
            UNREACHABLE
        }

        static Outcome loggedIn(String token, Account account) {
            return new Outcome(Kind.LOGGED_IN, token, account, null);
        }

        static Outcome registered(Account account) {
            return new Outcome(Kind.REGISTERED, null, account, null);
        }

        static Outcome wrongCredentials() {
            return new Outcome(Kind.WRONG_CREDENTIALS, null, null,
                    "Incorrect username or password");
        }

        static Outcome usernameTaken() {
            return new Outcome(Kind.USERNAME_TAKEN, null, null,
                    "That username is already taken");
        }

        static Outcome rejected(String message) {
            return new Outcome(Kind.REJECTED, null, null, message);
        }

        static Outcome unreachable() {
            return new Outcome(Kind.UNREACHABLE, null, null,
                    "The server could not be reached. You can still play offline.");
        }

        /** Whether this is a session the game can use. */
        public boolean isLoggedIn() {
            return kind == Kind.LOGGED_IN;
        }
    }
}
