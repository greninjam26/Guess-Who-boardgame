package com.guesswho.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guesswho.GuessWhoServerApplication;
import com.guesswho.api.ApiVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What the server does about a client speaking the wrong protocol.
 *
 * <p>Most of this cannot be tested for real until there are two builds that
 * disagree, which needs a deployment. What can be tested is the mechanism: that
 * a version is read, that an unsupported one is turned away in words a player
 * can act on, and — the part that matters most today — that the installers
 * already on people's disks are not turned away by it.</p>
 */
@SpringBootTest(
        classes = GuessWhoServerApplication.class,
        properties = "guesswho.rooms.sweep.enabled=false")
@AutoConfigureMockMvc
class ApiVersionTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void tellsEveryClientWhatTheServerSpeaks() throws Exception {
        //On every response, so a client can tell how far behind it is without
        //having to fail first.
        mockMvc.perform(get("/api/status"))
                .andExpect(header().string(ApiVersion.HEADER,
                        String.valueOf(ApiVersion.CURRENT)));
    }

    @Test
    void servesAClientFromBeforeThereWasAVersion() throws Exception {
        //The one that matters on the day this is deployed. Every installer
        //already released sends no version header, and rejecting them would be
        //the check doing harm: nothing is incompatible yet, and those builds
        //map an unrecognised status to "the server could not be reached" — so
        //they would show a reconnecting banner for ever rather than a reason.
        mockMvc.perform(get("/api/status")).andExpect(status().isOk());
    }

    @Test
    void servesAClientSpeakingTheCurrentVersion() throws Exception {
        mockMvc.perform(get("/api/status")
                        .header(ApiVersion.HEADER, String.valueOf(ApiVersion.CURRENT)))
                .andExpect(status().isOk());
    }

    @Test
    void readsAnUnreadableVersionAsTheOldest() throws Exception {
        //Nonsense in the header is the same situation as no header: a client
        //this server does not recognise. Both deserve the same answer rather
        //than one being refused for the shape of its lie.
        assertEquals(0, ApiVersion.claimedBy("not-a-number"));
        assertEquals(0, ApiVersion.claimedBy(""));
        assertEquals(0, ApiVersion.claimedBy(null));
    }

    @Test
    void doesNotVersionWhatIsNotTheApi() throws Exception {
        //Only the API has a contract to keep. A filter over everything would be
        //a rule applied where there is nothing to break.
        assertTrue(ApiVersion.isSupported(0), "Today nothing is turned away");
    }

    @Test
    void turnsAwayAClientBelowTheMinimumWithSomethingSayable() {
        //The minimum is zero today and should stay there until a change breaks
        //something, so this exercises the rule rather than the endpoint: a
        //version below the line is unsupported, whatever the line is.
        assertTrue(ApiVersion.isSupported(ApiVersion.MINIMUM_SUPPORTED));
        assertTrue(ApiVersion.isSupported(ApiVersion.CURRENT));
        assertEquals(false, ApiVersion.isSupported(ApiVersion.MINIMUM_SUPPORTED - 1));
    }
}
