package com.guesswho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the Guess Who Spring Boot server.
 */
@SpringBootApplication
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
