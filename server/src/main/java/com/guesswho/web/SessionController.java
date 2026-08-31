package com.guesswho.web;

import com.guesswho.account.Account;
import com.guesswho.account.Credentials;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Logging in and out.
 *
 * <p>A session is a resource: creating one is a login, deleting it is a logout,
 * and asking for the current one says who you are.</p>
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionService sessions;

    /**
     * @param sessions issues and resolves tokens
     */
    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    /**
     * Logs in.
     *
     * @param credentials the name and password typed at the prompt
     * @return the token to send with later requests
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoggedIn logIn(@RequestBody Credentials credentials) {
        if (credentials == null
                || credentials.username() == null || credentials.password() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A username and password are required");
        }
        return sessions.logIn(credentials.username(), credentials.password())
                .map(issued -> new LoggedIn(
                        issued.token(), issued.expiresAt(), issued.account()))
                //One message for both an unknown name and a wrong password.
                //Saying which would tell somebody whose names are worth
                //guessing at.
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Incorrect username or password"));
    }

    /**
     * Says who is logged in.
     *
     * @param authorization the Authorization header, if one was sent
     * @return the account the token belongs to
     */
    @GetMapping("/current")
    public Account current(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        return sessions.accountFor(BearerToken.from(authorization))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Not logged in"));
    }

    /**
     * Logs out.
     *
     * <p>Succeeds whether or not the token was any good: the caller wanted to
     * stop being logged in, and afterwards they are.</p>
     *
     * @param authorization the Authorization header, if one was sent
     */
    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logOut(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        sessions.logOut(BearerToken.from(authorization));
    }

    /**
     * A successful login.
     *
     * @param token     the token to send with later requests
     * @param expiresAt when it stops working
     * @param account   who logged in
     */
    public record LoggedIn(String token, Instant expiresAt, Account account) {
    }
}
