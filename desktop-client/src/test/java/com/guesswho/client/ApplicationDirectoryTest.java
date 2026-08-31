package com.guesswho.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApplicationDirectoryTest {
    @Test
    void putsMacFilesWhereMacKeepsThem() {
        assertEquals(
                Path.of("/Users/sam", "Library", "Application Support", "Guess Who"),
                ApplicationDirectory.resolve("Mac OS X", null, null, "/Users/sam"));
    }

    @Test
    void followsTheWindowsRoamingProfile() {
        assertEquals(
                Path.of("C:\\Users\\sam\\AppData\\Roaming", "Guess Who"),
                ApplicationDirectory.resolve(
                        "Windows 11", "C:\\Users\\sam\\AppData\\Roaming", null, "C:\\Users\\sam"));
    }

    @Test
    void fallsBackToTheUsualWindowsLocationWhenTheVariableIsMissing() {
        assertEquals(
                Path.of("C:\\Users\\sam", "AppData", "Roaming", "Guess Who"),
                ApplicationDirectory.resolve("Windows 11", null, null, "C:\\Users\\sam"));
        assertEquals(
                Path.of("C:\\Users\\sam", "AppData", "Roaming", "Guess Who"),
                ApplicationDirectory.resolve("Windows 11", "  ", null, "C:\\Users\\sam"));
    }

    @Test
    void honoursTheDataDirectoryLinuxUsersSet() {
        assertEquals(
                Path.of("/home/sam/.data", "guess-who"),
                ApplicationDirectory.resolve("Linux", null, "/home/sam/.data", "/home/sam"));
    }

    @Test
    void usesTheLinuxDefaultWhenNothingIsSet() {
        assertEquals(
                Path.of("/home/sam", ".local", "share", "guess-who"),
                ApplicationDirectory.resolve("Linux", null, null, "/home/sam"));
    }

    @Test
    void treatsAnUnfamiliarSystemAsUnix() {
        assertEquals(
                Path.of("/home/sam", ".local", "share", "guess-who"),
                ApplicationDirectory.resolve("SunOS", null, null, "/home/sam"));
    }

    @Test
    void neverReturnsAPathTheWorkingDirectoryDecides() {
        //The bug this class exists for: a relative path follows whichever
        //directory the launcher happened to start in, which for an installed
        //application is somewhere it cannot write.
        for (String system : new String[] {"Mac OS X", "Windows 11", "Linux", "SunOS"}) {
            Path directory =
                    ApplicationDirectory.resolve(system, "/appdata", "/datahome", "/home/sam");

            assertTrue(directory.isAbsolute(), system + " gave a relative path: " + directory);
        }
    }

    @Test
    void namesTheDirectoryAfterTheGame() {
        assertTrue(ApplicationDirectory.resolve("Mac OS X", null, null, "/Users/sam")
                .toString().contains("Guess Who"));
        assertTrue(ApplicationDirectory.resolve("Linux", null, null, "/home/sam")
                .toString().contains("guess-who"));
    }

    @Test
    void createsSomewhereWritableOnThisMachine() throws Exception {
        Path directory = ApplicationDirectory.forThisMachine();

        assertTrue(java.nio.file.Files.isDirectory(directory),
                "Should have created " + directory);
        assertTrue(java.nio.file.Files.isWritable(directory),
                "Should be able to write to " + directory);
    }
}
