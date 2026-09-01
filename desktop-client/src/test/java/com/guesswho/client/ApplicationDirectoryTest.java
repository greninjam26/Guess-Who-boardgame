package com.guesswho.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
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
        Path unwritable = anUnwritableDirectory("unwritable");
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
        Path unwritable = anUnwritableDirectory("also-unwritable");

        assertEquals(Path.of(""), ApplicationDirectory.firstUsable(unwritable));
    }

    /**
     * A directory that exists and cannot be written to.
     *
     * <p>Skipped rather than failed where that cannot be arranged. Permissions
     * are a POSIX idea, so Windows and any other filesystem without them throws
     * instead of refusing; and root can write to a directory whose permissions
     * say otherwise, which would make the test fail for a reason that has
     * nothing to do with the code it covers.</p>
     */
    private Path anUnwritableDirectory(String name) throws Exception {
        assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "Needs POSIX permissions to make a directory unwritable");
        Path unwritable = directory.resolve(name);
        Files.createDirectories(unwritable);
        Files.setPosixFilePermissions(unwritable, Set.of(PosixFilePermission.OWNER_READ));
        assumeFalse(Files.isWritable(unwritable),
                "Still writable after chmod — probably running as root");
        return unwritable;
    }

    @Test
    void createsSomewhereWritableOnThisMachine() {
        Path chosen = ApplicationDirectory.forThisMachine();

        assertTrue(Files.isDirectory(chosen), "Should have created " + chosen);
        //The whole promise: whatever it picked, it can be written to.
        assertTrue(Files.isWritable(chosen), "Should be able to write to " + chosen);
    }
}
