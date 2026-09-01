package com.guesswho.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class TokenStoreTest {
    @TempDir
    private Path directory;

    private TokenStore store;

    @BeforeEach
    void freshStore() {
        store = new TokenStore(directory.resolve("session-token"));
    }

    @Test
    void hasNoTokenUntilOneIsSaved() {
        assertTrue(store.read().isEmpty());
    }

    @Test
    void remembersATokenAcrossSessions() {
        assertTrue(store.save("a-token"));

        assertEquals("a-token",
                new TokenStore(directory.resolve("session-token")).read().orElseThrow());
    }

    @Test
    void replacesAPreviousToken() {
        store.save("first");
        store.save("second");

        assertEquals("second", store.read().orElseThrow());
    }

    @Test
    void forgetsTheTokenOnLogout() {
        store.save("a-token");

        store.clear();

        assertTrue(store.read().isEmpty());
    }

    @Test
    void clearingWhenThereIsNothingStoredIsHarmless() {
        store.clear();
        store.clear();
    }

    @Test
    void refusesToStoreNothing() {
        assertFalse(store.save(null));
        assertFalse(store.save(""));
        assertFalse(store.save("   "));
        assertTrue(store.read().isEmpty());
    }

    @Test
    void treatsAnEmptyFileAsNoToken() throws Exception {
        Files.writeString(directory.resolve("session-token"), "   ", StandardCharsets.UTF_8);

        assertTrue(store.read().isEmpty());
    }

    @Test
    void ignoresSurroundingWhitespace() throws Exception {
        //A token copied by hand, or a file an editor added a newline to.
        Files.writeString(directory.resolve("session-token"), "  a-token\n",
                StandardCharsets.UTF_8);

        assertEquals("a-token", store.read().orElseThrow());
    }

    @Test
    void reportsFailureRatherThanThrowingWhenItCannotWrite() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.createDirectories(blocked.resolve("session-token"));

        assertFalse(new TokenStore(blocked.resolve("session-token")).save("a-token"),
                "Failing to remember a login is a login prompt, not a crash");
    }

    @Test
    void readsNothingRatherThanThrowingWhenItCannotRead() throws Exception {
        Path directoryNotFile = directory.resolve("token-as-directory");
        Files.createDirectories(directoryNotFile);

        assertTrue(new TokenStore(directoryNotFile).read().isEmpty());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void keepsTheTokenReadableOnlyByItsOwner() throws Exception {
        //A token is a password by another name. Preferences on macOS are
        //world-readable, which is why this is a file and why it is locked down.
        //
        //The OS check is not quite the right question: permissions belong to
        //the filesystem, and a temporary directory on one without them throws
        //here on any operating system.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault()
                        .supportedFileAttributeViews().contains("posix"),
                "Needs a filesystem that has POSIX permissions to check them");
        store.save("a-token");

        Set<PosixFilePermission> permissions =
                Files.getPosixFilePermissions(directory.resolve("session-token"));

        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                permissions);
    }
}
