package com.guesswho;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The settings the deployed server runs under.
 *
 * <p>Properties, not behaviour. This context starts nothing — no servlet, no
 * database — so it can assert what the profile says without needing anything the
 * profile describes. What the forwarded-header setting actually does is a
 * different question, asked by {@code ForwardedAddressTest}, which needs a
 * servlet context this one deliberately does not have.</p>
 *
 * <p>Worth having at all because every one of these is invisible until it is
 * wrong in production: a stack trace reaching a stranger, a pool too large for
 * the host, a Flyway baseline quietly accepting a schema nobody recognises.</p>
 */
@SpringBootTest(
        classes = GuessWhoServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("aws")
@TestPropertySource(properties = {
        //The profile takes its datasource from the environment on the real host.
        //Here it needs something to point at that is not the developer's file.
        "spring.datasource.url=jdbc:h2:mem:aws-profile-test",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "guesswho.rooms.sweep.enabled=false"})
class AwsProfileTest {
    @Autowired
    private Environment environment;

    @Test
    void bindsOnlyToLoopbackSoOnlyTheProxyCanReachIt() {
        assertEquals("127.0.0.1", environment.getProperty("server.address"));
    }

    @Test
    void readsTheForwardedHeaderSoRateLimitsStayPerCaller() {
        //Without this, registering and signing in are limited per server rather
        //than per caller, because behind the proxy every caller looks like
        //127.0.0.1.
        assertEquals("FRAMEWORK", environment.getProperty("server.forward-headers-strategy"));
    }

    @Test
    void tellsAStrangerNothingAboutWhatWentWrong() {
        assertEquals("never", environment.getProperty("server.error.include-message"));
        assertEquals("never", environment.getProperty("server.error.include-stacktrace"));
        assertEquals("never", environment.getProperty("server.error.include-binding-errors"));
        assertEquals("false", environment.getProperty("server.error.include-exception"));
    }

    @Test
    void keepsTheConnectionPoolWithinASmallHost() {
        assertEquals("4", environment.getProperty("spring.datasource.hikari.maximum-pool-size"));
    }

    @Test
    void refusesToBaselineADatabaseItDoesNotRecognise() {
        assertEquals("false", environment.getProperty("spring.flyway.baseline-on-migrate"));
    }

    @Test
    void logsInAFormatCloudWatchCanSearch() {
        assertEquals("ecs", environment.getProperty("logging.structured.format.console"));
    }
}
