package com.guesswho.ui;

import com.guesswho.account.Account;
import com.guesswho.client.AccountClient;
import com.guesswho.client.TokenStore;
import java.util.Optional;

/**
 * Who is playing, as far as the game is concerned.
 *
 * <p>Either a signed-in account or a guest. A guest is not a lesser state to be
 * nagged out of: somebody who wants to try the game should be able to, and the
 * whole of it works without an account. What signing in buys is a leaderboard
 * row that belongs to you rather than to whoever typed your name.</p>
 */
class PlayerIdentity {
    private final AccountClient accounts;
    private final TokenStore tokens;

    private Account account;
    private String token;

    /**
     * @param accounts talks to the server
     * @param tokens   remembers the session between launches
     */
    PlayerIdentity(AccountClient accounts, TokenStore tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    /**
     * Tries to pick up a session left over from a previous launch.
     *
     * <p>Blocking, and deliberately so: it runs before the interface is shown,
     * and the alternative is a login screen that vanishes half a second after
     * somebody starts typing into it.</p>
     *
     * @return the account, or empty when there is no usable session
     */
    Optional<Account> resumePreviousSession() {
        Optional<String> stored = tokens.read();
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        //An unreachable server answers empty rather than failing, so this ends
        //at the login screen rather than stopping the game from starting.
        Optional<Account> found = accounts.whoAmI(stored.get()).join();
        if (found.isEmpty()) {
            //The token is no longer good. Keeping it would mean trying it again
            //on every launch for ever.
            tokens.clear();
            return Optional.empty();
        }
        account = found.get();
        token = stored.get();
        return found;
    }

    /**
     * Records a successful login and remembers it for next time.
     *
     * @param outcome the result of logging in
     */
    void signedIn(AccountClient.Outcome outcome) {
        if (!outcome.isLoggedIn()) {
            return;
        }
        account = outcome.account();
        token = outcome.token();
        tokens.save(token);
    }

    /**
     * Signs out, here and on the server.
     *
     * <p>Both, because forgetting the token locally leaves one that still works
     * and telling only the server leaves the game presenting a dead one.</p>
     */
    void signOut() {
        if (token != null) {
            accounts.logOut(token);
        }
        tokens.clear();
        account = null;
        token = null;
    }

    /** Whether somebody is signed in. */
    boolean isSignedIn() {
        return account != null;
    }

    /**
     * The signed-in account.
     *
     * @return the account, or empty when playing as a guest
     */
    Optional<Account> account() {
        return Optional.ofNullable(account);
    }

    /**
     * The name to put on a game, which a guest still has.
     *
     * @return the signed-in name, or empty for a guest to fill in themselves
     */
    Optional<String> username() {
        return account().map(Account::username);
    }

    /**
     * The token to send with a request, when there is one.
     *
     * @return the session token, or empty when playing as a guest
     */
    Optional<String> token() {
        return Optional.ofNullable(token);
    }
}
