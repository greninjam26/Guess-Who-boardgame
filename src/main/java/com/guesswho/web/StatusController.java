package com.guesswho.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports whether the Guess Who server is available.
 */
@RestController
@RequestMapping("/api")
public class StatusController {
    /**
     * Returns the current server status.
     *
     * @return an online status response
     */
    @GetMapping("/status")
    public StatusResponse getStatus() {
        return new StatusResponse("online");
    }

    /**
     * JSON response returned by the status endpoint.
     *
     * @param status current server status
     */
    public record StatusResponse(String status) {
    }
}
