package com.guesswho.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reports whether the Guess Who server is available.
 *
 * <p>Available means the database answered, not merely that this process is
 * running. A server whose database has gone can still return a cheerful string
 * from memory, and something watching that string would keep sending players to
 * a server where every request fails — the check would be confirming the one
 * part of the system that cannot be broken.</p>
 */
@RestController
@RequestMapping("/api")
public class StatusController {
    private final JdbcOperations jdbc;

    /**
     * @param jdbc used to ask the database whether it is there
     */
    public StatusController(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the current server status.
     *
     * @return an online status response
     * @throws ResponseStatusException 503 when the database cannot answer
     */
    @GetMapping("/status")
    public StatusResponse getStatus() {
        try {
            //The cheapest question that still requires a working connection, a
            //live pool and a database that will execute statements.
            jdbc.queryForObject("SELECT 1", Integer.class);
            return new StatusResponse("online");
        }
        catch (DataAccessException unavailable) {
            //A fixed reason, and the exception deliberately not in it. This is
            //the most reachable endpoint on the server, so its failure message
            //is the easiest thing in the system for a stranger to read — and a
            //driver's error text will happily name the host, the port, the
            //database and the user it failed to connect as.
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Temporarily unavailable", unavailable);
        }
    }

    /**
     * JSON response returned by the status endpoint.
     *
     * @param status current server status
     */
    public record StatusResponse(String status) {
    }
}
