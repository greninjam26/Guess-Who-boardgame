package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.guesswho.GuessWhoServerApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * That a caller behind the proxy is still identified as themselves.
 *
 * <p>Its own class, and its own kind of test. {@code AwsProfileTest} asserts the
 * property is set; this asserts what setting it does — and it needs a servlet
 * context to do that, which the profile test's {@code WebEnvironment.NONE}
 * cannot provide.</p>
 *
 * <p>The failure being guarded against is quiet. Behind Caddy, every request
 * arrives from {@code 127.0.0.1}, so the two address-keyed limits — registering
 * and signing in — silently stop being per-caller and become one bucket shared
 * by the whole internet. From a single client that looks exactly like working
 * rate limiting, which is why it takes two addresses to see it.</p>
 *
 * <p>The property is set here rather than inherited from the {@code aws} profile
 * so this test states its own precondition instead of depending on a profile it
 * is not otherwise exercising.</p>
 */
@SpringBootTest(
        classes = GuessWhoServerApplication.class,
        properties = {
                "guesswho.rate-limits.enabled=true",
                "guesswho.rooms.sweep.enabled=false",
                "server.forward-headers-strategy=FRAMEWORK"})
@AutoConfigureMockMvc
class ForwardedAddressTest {
    private static final String ONE = "203.0.113.1";
    private static final String ANOTHER = "203.0.113.2";

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
        limiter.reset();
    }

    @Test
    void oneAddressExhaustingItsAllowanceDoesNotSpendAnothers() {
        //The whole point. If this fails, one person guessing passwords locks out
        //every other player on the server.
        exhaustSignInAllowance(ONE);

        assertEquals(false, isRefused(signIn(ANOTHER)),
                "A second address was refused because the first had been busy");
    }

    @Test
    void theSameAddressIsStillHeldToItsOwnAllowance() {
        //The other half: reading the header must not turn the limit off.
        exhaustSignInAllowance(ONE);

        assertTrue(isRefused(signIn(ONE)),
                "An address that spent its allowance was served anyway");
    }

    @Test
    void registeringIsAlsoCountedPerAddress() {
        for (int attempt = 0; attempt < 20; attempt++) {
            register(ONE, "player" + attempt);
        }

        assertEquals(false, isRefused(register(ANOTHER, "somebody-else")),
                "Registration limits collapsed into one bucket for every caller");
    }

    private void exhaustSignInAllowance(String address) {
        for (int attempt = 0; attempt < 40 && !isRefused(signIn(address)); attempt++) {
            //Until the server says no, which is the state these tests start from.
        }
    }

    private MvcResult signIn(String address) {
        try {
            return mockMvc.perform(post("/api/sessions")
                            .header("X-Forwarded-For", address)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong-guess\"}"))
                    .andReturn();
        }
        catch (Exception failed) {
            throw new AssertionError(failed);
        }
    }

    private MvcResult register(String address, String username) {
        try {
            return mockMvc.perform(post("/api/accounts")
                            .header("X-Forwarded-For", address)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username
                                    + "\",\"password\":\"a-good-password\"}"))
                    .andReturn();
        }
        catch (Exception failed) {
            throw new AssertionError(failed);
        }
    }

    private static boolean isRefused(MvcResult result) {
        return result.getResponse().getStatus() == 429;
    }
}
