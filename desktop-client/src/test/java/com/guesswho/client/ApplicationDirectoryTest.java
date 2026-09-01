package com.guesswho.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApplicationDirectoryTest {
    @org.junit.jupiter.api.io.TempDir
    private Path directory;

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
    void skipsADirectoryItCannotWriteTo() throws Exception {
        //Creating a directory succeeds when it already exists, whatever its
        //permissions. Existing is not the same as usable, and a path that
        //cannot be written to looks fine until the first save fails.
        Path unwritable = directory.resolve("unwritable");
        Files.createDirectories(unwritable);
        Files.setPosixFilePermissions(unwritable, Set.of(PosixFilePermission.OWNER_READ));
        Path writable = directory.resolve("writable");

        assertEquals(writable, ApplicationDirectory.firstUsable(unwritable, writable));
    }

    @Test
    void prefersTheFirstUsableOne() {
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");

        assertEquals(first, ApplicationDirectory.firstUsable(first, second));
    }

    @Test
    void skipsAPathThatCannotBeADirectory() throws Exception {
        //A file where a directory should be: creating it fails outright.
        Path file = directory.resolve("a-file");
        Files.writeString(file, "not a directory");
        Path writable = directory.resolve("writable");

        assertEquals(writable, ApplicationDirectory.firstUsable(file, writable));
    }

    @Test
    void ignoresACandidateThatCouldNotBeWorkedOut() {
        //A property or variable can be refused outright, leaving nothing to try.
        Path writable = directory.resolve("writable");

        assertEquals(writable, ApplicationDirectory.firstUsable(null, writable));
    }

    @Test
    void fallsBackToTheWorkingDirectoryWhenNothingElseWorks() throws Exception {
        Path unwritable = directory.resolve("also-unwritable");
        Files.createDirectories(unwritable);
        Files.setPosixFilePermissions(unwritable, Set.of(PosixFilePermission.OWNER_READ));

        assertEquals(Path.of(""), ApplicationDirectory.firstUsable(unwritable));
    }

    @Test
    void createsSomewhereWritableOnThisMachine() {
        Path chosen = ApplicationDirectory.forThisMachine();

        assertTrue(Files.isDirectory(chosen), "Should have created " + chosen);
        //The whole promise: whatever it picked, it can be written to.
        assertTrue(Files.isWritable(chosen), "Should be able to write to " + chosen);
    }
}
