package com.guesswho.web;

import com.guesswho.account.Account;
import com.guesswho.account.Credentials;
import com.guesswho.persistence.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registers players.
 *
 * <p>Every rule here exists because a username outlives the moment it is
 * chosen: it goes on a leaderboard, into a game record, and eventually into a
 * room somebody else joins.</p>
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    /** Matches the column in V5__add_accounts.sql. */
    private static final int MAX_USERNAME = 32;
    private static final int MIN_USERNAME = 3;
    /** Long enough to be worth hashing; the rest is the player's business. */
    private static final int MIN_PASSWORD = 8;
    /** BCrypt silently ignores anything past 72 bytes, so refuse it instead. */
    private static final int MAX_PASSWORD = 72;

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter limiter;

    /**
     * @param accounts        where registered players are kept
     * @param passwordEncoder hashes passwords on the way in
     * @param limiter         bounds how fast accounts can be created
     */
    public AccountController(AccountRepository accounts, PasswordEncoder passwordEncoder,
            RateLimiter limiter) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
    }

    /**
     * Registers a player.
     *
     * @param credentials the name and password they chose
     * @return the new account, without anything secret in it
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account register(
            @RequestBody Credentials credentials, jakarta.servlet.http.HttpServletRequest from) {
        Callers.require(limiter, "register", from, RateLimits.REGISTER);
        validate(credentials);
        try {
            return accounts.create(credentials.username().trim(),
                    passwordEncoder.encode(credentials.password()));
        }
        catch (AccountRepository.UsernameTakenException taken) {
            //409 rather than 400: the request was well formed, somebody else
            //just got there first, and the client should say so differently.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "That username is already taken");
        }
    }

    private void validate(Credentials credentials) {
        if (credentials == null
                || credentials.username() == null || credentials.password() == null) {
            throw badRequest("A username and password are required");
        }
        String username = credentials.username().trim();
        if (username.length() < MIN_USERNAME || username.length() > MAX_USERNAME) {
            throw badRequest("Username must be between " + MIN_USERNAME + " and "
                    + MAX_USERNAME + " characters");
        }
        //Letters, digits, underscore and hyphen. Anything else — spaces at the
        //ends, control characters, look-alikes from other scripts — makes a
        //name that cannot be typed back reliably or that impersonates another.
        if (!username.matches("[A-Za-z0-9_-]+")) {
            throw badRequest(
                    "Username may contain only letters, digits, underscores and hyphens");
        }
        if (credentials.password().length() < MIN_PASSWORD) {
            throw badRequest("Password must be at least " + MIN_PASSWORD + " characters");
        }
        if (credentials.password().length() > MAX_PASSWORD) {
            throw badRequest("Password must be at most " + MAX_PASSWORD + " characters");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
