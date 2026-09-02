package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guesswho.GuessWhoServerApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * The limits, on the endpoints they actually protect.
 *
 * <p>The rest of the suite runs with limiting switched off, because it registers
 * hundreds of accounts from one address and would spend a real allowance in its
 * first few classes. This one turns it back on, so the wiring is covered rather
 * than only the counting: a policy that is never reached from a controller
 * protects nothing, and nothing else here would notice.</p>
 */
@SpringBootTest(
        classes = GuessWhoServerApplication.class,
        properties = {"guesswho.rate-limits.enabled=true", "guesswho.rooms.sweep.enabled=false"})
@AutoConfigureMockMvc
class RateLimitedEndpointsTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RateLimiter limiter;

    @BeforeEach
    void nobodyHasSpentAnything() {
        jdbcTemplate.update("DELETE FROM account_sessions");
        jdbcTemplate.update("DELETE FROM accounts");
        //Shared state like any other, and cleared for the same reason the tables
        //are: one test's attempts must not be another's.
        limiter.reset();
    }

    @Test
    void refusesAFloodOfSignInAttempts() throws Exception {
        //The one that matters most. Every attempt costs a BCrypt hash, so an
        //unlimited login endpoint both leaks passwords and burns the CPU.
        int refusals = 0;
        for (int attempt = 0; attempt < 40; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong-guess\"}"))
                    .andReturn();
            if (result.getResponse().getStatus() == 429) {
                refusals++;
            }
        }

        assertTrue(refusals > 0, "Passwords can be guessed at as fast as the server will answer");
    }

    @Test
    void stopsRefusingOnceTheAllowanceComesBack() throws Exception {
        //A limit that never lifts is a ban, and a player who mistyped their
        //password four times has done nothing wrong.
        for (int attempt = 0; attempt < 40; attempt++) {
            mockMvc.perform(post("/api/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"nobody\",\"password\":\"wrong-guess\"}"));
        }

        limiter.reset();

        //Unauthorized, not too-many-requests: refused on the password again
        //rather than on the count.
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong-guess\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAFloodOfNewAccounts() throws Exception {
        int refusals = 0;
        for (int attempt = 0; attempt < 20; attempt++) {
            MvcResult result = mockMvc.perform(post("/api/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"player" + attempt
                                    + "\",\"password\":\"a-good-password\"}"))
                    .andReturn();
            if (result.getResponse().getStatus() == 429) {
                refusals++;
            }
        }

        assertTrue(refusals > 0, "Accounts can be created as fast as the server will answer");
    }

    @Test
    void saysTooManyRequestsRatherThanRefusingOutright() throws Exception {
        //429 rather than 403: the request was allowed, there have just been too
        //many, and waiting is the remedy. A client can act on that.
        int status = 200;
        for (int attempt = 0; attempt < 40 && status != 429; attempt++) {
            status = mockMvc.perform(post("/api/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong-guess\"}"))
                    .andReturn().getResponse().getStatus();
        }

        assertEquals(429, status);
    }
}
