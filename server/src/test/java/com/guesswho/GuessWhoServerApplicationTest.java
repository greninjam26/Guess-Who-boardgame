package com.guesswho;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = GuessWhoServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GuessWhoServerApplicationTest {
    @Test
    void applicationContextStarts() {
    }
}
