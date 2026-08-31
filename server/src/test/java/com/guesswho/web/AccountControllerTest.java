package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.persistence.AccountRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = GuessWhoServerApplication.class)
@AutoConfigureMockMvc
class AccountControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearAccounts() {
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    void registersAPlayer() throws Exception {
        register("greninja", "a-good-password")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("greninja"))
                .andExpect(jsonPath("$.id").isNumber());

        assertTrue(accounts.exists("greninja"));
    }

    @Test
    void neverReturnsThePassword() throws Exception {
        //A response that carries a hash is one that will end up in a log.
        register("greninja", "a-good-password")
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void storesTheHashRatherThanThePassword() throws Exception {
        register("greninja", "a-good-password").andExpect(status().isCreated());

        String stored = accounts.findByUsername("greninja").orElseThrow().passwordHash();

        assertNotEquals("a-good-password", stored);
        assertFalse(stored.contains("a-good-password"));
        assertTrue(passwordEncoder.matches("a-good-password", stored),
                "The stored hash has to still verify the password it came from");
    }

    @Test
    void hashesTheSamePasswordDifferentlyForTwoPeople() throws Exception {
        //BCrypt salts each hash. Identical hashes would mean identical
        //passwords are visible as such to anyone reading the table.
        register("alex", "the-same-password").andExpect(status().isCreated());
        register("blake", "the-same-password").andExpect(status().isCreated());

        assertNotEquals(
                accounts.findByUsername("alex").orElseThrow().passwordHash(),
                accounts.findByUsername("blake").orElseThrow().passwordHash());
    }

    @Test
    void refusesANameSomebodyAlreadyHas() throws Exception {
        register("greninja", "a-good-password").andExpect(status().isCreated());

        register("greninja", "another-password").andExpect(status().isConflict());

        assertEquals(1, accountCount());
    }

    @Test
    void refusesANameThatDiffersOnlyByCase() throws Exception {
        //Otherwise two accounts look identical on a leaderboard and either can
        //be mistaken for the other.
        register("greninja", "a-good-password").andExpect(status().isCreated());

        register("Greninja", "another-password").andExpect(status().isConflict());
        register("GRENINJA", "another-password").andExpect(status().isConflict());

        assertEquals(1, accountCount());
    }

    @Test
    void keepsTheNameAsItWasTyped() throws Exception {
        register("Greninja", "a-good-password").andExpect(status().isCreated());

        assertEquals("Greninja",
                accounts.findByUsername("greninja").orElseThrow().account().username(),
                "Found regardless of case, but shown the way they wrote it");
    }

    @Test
    void trimsSurroundingSpaceFromTheName() throws Exception {
        register("  greninja  ", "a-good-password").andExpect(status().isCreated());

        assertEquals("greninja",
                accounts.findByUsername("greninja").orElseThrow().account().username());
    }

    @ParameterizedTest
    @MethodSource("unusableNames")
    void refusesANameThatCannotBeUsed(String username) throws Exception {
        register(username, "a-good-password").andExpect(status().isBadRequest());

        assertEquals(0, accountCount());
    }

    private static Stream<String> unusableNames() {
        return Stream.of(
                "ab",                                   //too short to be worth having
                "a".repeat(33),                         //wider than the column
                "has space",                            //cannot be typed back reliably
                "semi;colon",
                "quote'mark",
                "<script>",
                "",
                "   ");
    }

    @ParameterizedTest
    @MethodSource("unusablePasswords")
    void refusesAPasswordThatCannotBeUsed(String password) throws Exception {
        register("greninja", password).andExpect(status().isBadRequest());

        assertEquals(0, accountCount());
    }

    private static Stream<String> unusablePasswords() {
        return Stream.of(
                "short",
                "",
                //BCrypt ignores everything past 72 bytes, so a longer password
                //would be silently truncated and the extra typing wasted.
                "a".repeat(73));
    }

    @Test
    void acceptsAPasswordRightUpToTheHashingLimit() throws Exception {
        register("greninja", "a".repeat(72)).andExpect(status().isCreated());
    }

    @Test
    void refusesARequestWithNothingInIt() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers -------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions register(
            String username, String password) throws Exception {
        return mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}"
                        .formatted(username, password)));
    }

    private int accountCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts", Integer.class);
        return count == null ? 0 : count;
    }
}
