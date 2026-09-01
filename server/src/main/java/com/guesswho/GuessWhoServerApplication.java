package com.guesswho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the Guess Who Spring Boot server.
 */
//No default user. Spring Security invents one and logs its password on every
//start, which is noise at best and, on a deployed server, an invitation to
//wonder whether it means anything. Authentication here is a bearer token
//checked against the sessions table; there is no form login to have a user for.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
//For the room sweep. Nothing else is scheduled.
@EnableScheduling
public class GuessWhoServerApplication {
    /**
     * Starts the embedded web server.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(GuessWhoServerApplication.class, args);
    }
}
