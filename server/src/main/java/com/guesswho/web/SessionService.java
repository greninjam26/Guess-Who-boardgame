package com.guesswho.web;

import com.guesswho.account.Account;
import com.guesswho.persistence.AccountRepository;
import com.guesswho.persistence.SessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Logging in, logging out, and working out who a token belongs to.
 *
 * <p>The token is handed to the client once and never stored anywhere the
 * server can read it back. What is kept is its hash, so a copy of the database
 * gives an attacker nothing they can present.</p>
 */
@Service
public class SessionService {
    /** 256 bits of randomness: nothing to guess, so nothing to rate-limit. */
    private static final int TOKEN_BYTES = 32;
    /** Long enough not to nag, short enough that a stolen token stops working. */
    private static final Duration LIFETIME = Duration.ofDays(30);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordEncoder passwordEncoder;

    /**
     * @param accounts        registered players
     * @param sessions        who is currently logged in
     * @param passwordEncoder checks a password against a stored hash
     */
    public SessionService(AccountRepository accounts, SessionRepository sessions,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Checks a password and, if it is right, starts a session.
     *
     * @param username the name typed at the prompt
     * @param password the password typed at the prompt
     * @return the token and who it belongs to, or empty when either was wrong
     */
    public Optional<IssuedToken> logIn(String username, String password) {
        Optional<AccountRepository.StoredAccount> stored = accounts.findByUsername(username);
        if (stored.isEmpty()) {
            //Hash the given password against nothing anyway. Returning early
            //here makes an unknown username answer faster than a wrong
            //password, which tells an attacker which names are worth trying.
            passwordEncoder.matches(password, NON_MATCHING_HASH);
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, stored.get().passwordHash())) {
            return Optional.empty();
        }

        String token = newToken();
        Instant expiry = Instant.now().plus(LIFETIME);
        sessions.create(stored.get().account().id(), hash(token), expiry);
        return Optional.of(new IssuedToken(token, expiry, stored.get().account()));
    }

    /**
     * Works out who is making a request.
     *
     * @param token the token presented, or null when none was
     * @return the account, or empty when the token is missing, unknown or expired
     */
    public Optional<Account> accountFor(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findAccount(hash(token), Instant.now());
    }

    /**
     * Ends a session.
     *
     * @param token the token being given up
     */
    public void logOut(String token) {
        if (token != null && !token.isBlank()) {
            sessions.delete(hash(token));
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        //URL-safe and unpadded, so it survives being put in a header or a file
        //without anything needing to escape it.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a token for storage and lookup.
     *
     * <p>Plain SHA-256, deliberately: a token is 256 random bits, so there is
     * no dictionary for an attacker to work through and nothing to be gained by
     * making this slow. Passwords are different and use BCrypt.</p>
     */
    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime",
                    impossible);
        }
    }

    /** A real BCrypt hash of a value nobody knows, used only to spend the time. */
    private static final String NON_MATCHING_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    /**
     * A freshly issued token, on its way to the client that will hold it.
     *
     * @param token     the token itself; the server keeps only its hash
     * @param expiresAt when it stops working
     * @param account   who it belongs to
     */
    public record IssuedToken(String token, Instant expiresAt, Account account) {
    }
}
