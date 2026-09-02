package com.guesswho.web;

import com.guesswho.GuessWhoServerApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Whether the server says it is online, and what it means by that.
 *
 * <p>"Online" has to mean the database answered. A server whose database has
 * gone reports itself healthy otherwise, so a load balancer keeps sending it
 * players and every one of them fails at the first request — the check would be
 * confirming the one part of the system that cannot be broken.</p>
 */
@SpringBootTest(classes = GuessWhoServerApplication.class)
@AutoConfigureMockMvc
class StatusControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reportsThatTheServerIsOnline() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("online"));
    }

    @Test
    void saysOnlineOnlyAfterTheDatabaseHasAnswered() {
        //Against the real datasource, so this passes for the reason it claims.
        assertEquals("online", new StatusController(jdbcTemplate).getStatus().status());
    }

    @Test
    void reportsServiceUnavailableWhenTheDatabaseCannotRespond() {
        //A datasource pointing at nothing, rather than a mock that has been told
        //to throw. The point is that a real connection failure becomes a 503,
        //and a stub asked to throw the exception the code already catches would
        //prove only that the catch block compiles.
        ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                () -> new StatusController(new JdbcTemplate(nowhere())).getStatus());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, refused.getStatusCode());
    }

    @Test
    void doesNotPutTheDatabaseFailureInThePublicReason() {
        //The status endpoint is the most reachable thing on the server. Its
        //failure message must not describe the database behind it.
        ResponseStatusException refused = assertThrows(ResponseStatusException.class,
                () -> new StatusController(new JdbcTemplate(nowhere())).getStatus());

        String reason = String.valueOf(refused.getReason());
        assertEquals(false, reason.toLowerCase().contains("jdbc"), reason);
        assertEquals(false, reason.toLowerCase().contains("connection"), reason);
    }

    /** A datasource whose database does not exist and cannot be created. */
    private static DriverManagerDataSource nowhere() {
        DriverManagerDataSource broken = new DriverManagerDataSource();
        broken.setDriverClassName("org.h2.Driver");
        //IFEXISTS stops H2 helpfully creating the database this is meant to fail
        //to reach.
        broken.setUrl("jdbc:h2:mem:no-such-database;IFEXISTS=TRUE");
        broken.setUsername("sa");
        broken.setPassword("");
        return broken;
    }
}
