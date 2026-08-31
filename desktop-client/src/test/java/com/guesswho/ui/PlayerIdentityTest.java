package com.guesswho.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guesswho.account.Account;
import com.guesswho.client.AccountClient;
import com.guesswho.client.TokenStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerIdentityTest {
    @TempDir
    private Path directory;

    private TokenStore tokens;
    private final List<String> loggedOut = new ArrayList<>();

    @BeforeEach
    void freshStore() {
        tokens = new TokenStore(directory.resolve("session-token"));
    }

    @Test
    void startsAsAGuest() {
        PlayerIdentity identity = new PlayerIdentity(serverSaying(Optional.empty()), tokens);

        assertFalse(identity.isSignedIn());
        assertTrue(identity.username().isEmpty());
        assertTrue(identity.token().isEmpty());
    }

    @Test
    void picksUpASessionFromAPreviousLaunch() {
        tokens.save("a-token");
        PlayerIdentity identity = new PlayerIdentity(
                serverSaying(Optional.of(new Account(7, "greninja"))), tokens);

        assertTrue(identity.resumePreviousSession().isPresent());

        assertTrue(identity.isSignedIn());
        assertEquals("greninja", identity.username().orElseThrow());
    }

    @Test
    void hasNothingToResumeWithoutAStoredToken() {
        PlayerIdentity identity = new PlayerIdentity(
                serverSaying(Optional.of(new Account(7, "greninja"))), tokens);

        assertTrue(identity.resumePreviousSession().isEmpty());
    }

    @Test
    void throwsAwayATokenTheServerNoLongerAccepts() {
        //Keeping it would mean trying the same dead token on every launch.
        tokens.save("a-stale-token");
        PlayerIdentity identity = new PlayerIdentity(serverSaying(Optional.empty()), tokens);

        assertTrue(identity.resumePreviousSession().isEmpty());

        assertTrue(tokens.read().isEmpty());
    }

    @Test
    void remembersASuccessfulSignIn() {
        PlayerIdentity identity = new PlayerIdentity(serverSaying(Optional.empty()), tokens);

        identity.signedIn(loggedIn("a-token", new Account(7, "greninja")));

        assertTrue(identity.isSignedIn());
        assertEquals("a-token", tokens.read().orElseThrow(),
                "A sign-in that is not remembered is one the player has to repeat");
    }

    @Test
    void ignoresAnOutcomeThatIsNotASignIn() {
        PlayerIdentity identity = new PlayerIdentity(serverSaying(Optional.empty()), tokens);

        identity.signedIn(unreachable());

        assertFalse(identity.isSignedIn());
        assertTrue(tokens.read().isEmpty());
    }

    @Test
    void signsOutBothHereAndOnTheServer() {
        //Only locally leaves a token that still works; only remotely leaves the
        //game presenting a dead one.
        PlayerIdentity identity = new PlayerIdentity(serverSaying(Optional.empty()), tokens);
        identity.signedIn(loggedIn("a-token", new Account(7, "greninja")));

        identity.signOut();

        assertFalse(identity.isSignedIn());
        assertTrue(tokens.read().isEmpty());
        assertEquals(List.of("a-token"), loggedOut);
    }

    @Test
    void signingOutAsAGuestTellsTheServerNothing() {
        new PlayerIdentity(serverSaying(Optional.empty()), tokens).signOut();

        assertTrue(loggedOut.isEmpty());
    }

    // --- helpers -------------------------------------------------------

    /** Built through the public constructor: the factories are not visible here. */
    private static AccountClient.Outcome unreachable() {
        return new AccountClient.Outcome(AccountClient.Outcome.Kind.UNREACHABLE,
                null, null, "The server could not be reached. You can still play offline.");
    }

    private static AccountClient.Outcome loggedIn(String token, Account account) {
        return new AccountClient.Outcome(
                AccountClient.Outcome.Kind.LOGGED_IN, token, account, null);
    }

    private AccountClient serverSaying(Optional<Account> whoAmI) {
        return new AccountClient() {
            @Override
            public CompletableFuture<Outcome> register(String username, String password) {
                return CompletableFuture.completedFuture(unreachable());
            }

            @Override
            public CompletableFuture<Outcome> logIn(String username, String password) {
                return CompletableFuture.completedFuture(unreachable());
            }

            @Override
            public CompletableFuture<Optional<Account>> whoAmI(String token) {
                return CompletableFuture.completedFuture(whoAmI);
            }

            @Override
            public CompletableFuture<Void> logOut(String token) {
                loggedOut.add(token);
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
