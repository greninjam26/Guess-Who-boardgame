package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.persistence.SessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = GuessWhoServerApplication.class)
@AutoConfigureMockMvc
class SessionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAccounts() throws Exception {
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");
        register("greninja", "a-good-password");
    }

    @Test
    void logsInWithTheRightPassword() throws Exception {
        logIn("greninja", "a-good-password")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.account.username").value("greninja"))
                .andExpect(jsonPath("$.expiresAt").isString());
    }

    @Test
    void neverReturnsAPasswordOrItsHash() throws Exception {
        logIn("greninja", "a-good-password")
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void refusesAWrongPassword() throws Exception {
        logIn("greninja", "not-the-password").andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAnUnknownName() throws Exception {
        logIn("nobody", "a-good-password").andExpect(status().isUnauthorized());
    }

    @Test
    void saysTheSameThingForAWrongNameAndAWrongPassword() throws Exception {
        //Different messages would tell somebody which usernames exist, and
        //therefore which are worth guessing passwords for.
        String wrongPassword = logIn("greninja", "not-the-password")
                .andReturn().getResponse().getErrorMessage();
        String wrongName = logIn("nobody", "a-good-password")
                .andReturn().getResponse().getErrorMessage();

        assertEquals(wrongPassword, wrongName);
    }

    @Test
    void logsInRegardlessOfHowTheNameIsCapitalised() throws Exception {
        logIn("GRENINJA", "a-good-password").andExpect(status().isCreated());
    }

    @Test
    void issuesADifferentTokenEachTime() throws Exception {
        assertNotEquals(tokenFrom(logIn("greninja", "a-good-password")),
                tokenFrom(logIn("greninja", "a-good-password")),
                "Two logins sharing a token means logging out of one ends both");
    }

    @Test
    void storesTheTokensHashRatherThanTheToken() throws Exception {
        String token = tokenFrom(logIn("greninja", "a-good-password"));

        String stored = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM account_sessions", String.class);

        assertNotEquals(token, stored);
        assertFalse(stored.contains(token),
                "A database that holds usable tokens is one that leaks logins");
    }

    @Test
    void saysWhoIsLoggedIn() throws Exception {
        String token = tokenFrom(logIn("greninja", "a-good-password"));

        mockMvc.perform(get("/api/sessions/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("greninja"));
    }

    @Test
    void refusesToSayWhoIsLoggedInWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/sessions/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAMadeUpToken() throws Exception {
        mockMvc.perform(get("/api/sessions/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesATokenSentWithoutTheBearerPrefix() throws Exception {
        String token = tokenFrom(logIn("greninja", "a-good-password"));

        mockMvc.perform(get("/api/sessions/current").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stopsAcceptingATokenAfterLoggingOut() throws Exception {
        String token = tokenFrom(logIn("greninja", "a-good-password"));

        mockMvc.perform(delete("/api/sessions/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sessions/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loggingOutTwiceIsHarmless() throws Exception {
        String token = tokenFrom(logIn("greninja", "a-good-password"));

        mockMvc.perform(delete("/api/sessions/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        //The caller wanted to stop being logged in, and they have.
        mockMvc.perform(delete("/api/sessions/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void loggingOutOfOneSessionLeavesTheOtherAlone() throws Exception {
        String phone = tokenFrom(logIn("greninja", "a-good-password"));
        String laptop = tokenFrom(logIn("greninja", "a-good-password"));

        mockMvc.perform(delete("/api/sessions/current")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + phone));

        mockMvc.perform(get("/api/sessions/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + laptop))
                .andExpect(status().isOk());
    }

    @Test
    void refusesAnExpiredToken() throws Exception {
        long accountId = jdbcTemplate.queryForObject(
                "SELECT id FROM accounts", Long.class);
        sessions.create(accountId, "0".repeat(64),
                Instant.now().minus(1, ChronoUnit.MINUTES));

        assertTrue(sessions.findAccount("0".repeat(64), Instant.now()).isEmpty(),
                "A session past its expiry must not resolve to anybody");
    }

    @Test
    void clearsOutExpiredSessionsWithoutTouchingLiveOnes() throws Exception {
        long accountId = jdbcTemplate.queryForObject("SELECT id FROM accounts", Long.class);
        sessions.create(accountId, "a".repeat(64), Instant.now().minus(1, ChronoUnit.DAYS));
        sessions.create(accountId, "b".repeat(64), Instant.now().plus(1, ChronoUnit.DAYS));

        assertEquals(1, sessions.deleteExpired(Instant.now()));
        assertTrue(sessions.findAccount("b".repeat(64), Instant.now()).isPresent());
    }

    @Test
    void endsEverySessionForOneAccount() throws Exception {
        //What a password change has to do, and what somebody who thinks they
        //have been compromised needs.
        tokenFrom(logIn("greninja", "a-good-password"));
        String second = tokenFrom(logIn("greninja", "a-good-password"));
        long accountId = jdbcTemplate.queryForObject("SELECT id FROM accounts", Long.class);

        assertEquals(2, sessions.deleteAllFor(accountId));

        assertTrue(sessionService.accountFor(second).isEmpty());
    }

    // --- helpers -------------------------------------------------------

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(username, password)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions logIn(
            String username, String password) throws Exception {
        return mockMvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(username, password)));
    }

    private static String credentials(String username, String password) {
        return "{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password);
    }

    private static String tokenFrom(org.springframework.test.web.servlet.ResultActions login)
            throws Exception {
        String body = login.andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"token\":\"") + 9;
        return body.substring(start, body.indexOf('"', start));
    }
}
