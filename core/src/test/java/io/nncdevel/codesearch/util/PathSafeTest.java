package io.nncdevel.codesearch.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathSafeTest {

    @Test
    void emptyAndNullDenoteRoot() {
        assertEquals("", PathSafe.validateRelative(""));
        assertEquals("", PathSafe.validateRelative(null));
    }

    @Test
    void normalizesRedundantSegments() {
        assertEquals("src/main", PathSafe.validateRelative("./src//main/"));
        assertEquals("a/b", PathSafe.validateRelative("a/b"));
    }

    @Test
    void rejectsAbsolutePath() {
        ToolException ex = assertThrows(ToolException.class, () -> PathSafe.validateRelative("/etc/passwd"));
        assertEquals(ErrorCode.INVALID_PATH, ex.code());
    }

    @Test
    void rejectsParentTraversal() {
        ToolException ex = assertThrows(ToolException.class, () -> PathSafe.validateRelative("a/../../b"));
        assertEquals(ErrorCode.INVALID_PATH, ex.code());
    }

    @Test
    void rejectsBackslash() {
        assertThrows(ToolException.class, () -> PathSafe.validateRelative("a\\b"));
    }

    @Test
    void resolvesExistingFileWithinRoot(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("src"));
        Path file = Files.createFile(root.resolve("src/App.java"));
        Path resolved = PathSafe.resolveWithin(root, "src/App.java");
        assertEquals(file.toRealPath(), resolved);
    }

    @Test
    void resolveRootReturnsRoot(@TempDir Path root) throws IOException {
        assertEquals(root.toRealPath(), PathSafe.resolveWithin(root, ""));
    }

    @Test
    void resolveMissingThrowsPathNotFound(@TempDir Path root) {
        ToolException ex = assertThrows(ToolException.class, () -> PathSafe.resolveWithin(root, "nope.txt"));
        assertEquals(ErrorCode.PATH_NOT_FOUND, ex.code());
    }

    @Test
    void resolveSymlinkEscapeRejected(@TempDir Path root, @TempDir Path outside) throws IOException {
        Path secret = Files.createFile(outside.resolve("secret.txt"));
        Path link = root.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, secret);
        } catch (UnsupportedOperationException | IOException e) {
            return; // symlinks unsupported on this platform; skip
        }
        ToolException ex = assertThrows(ToolException.class, () -> PathSafe.resolveWithin(root, "link.txt"));
        assertSame(ErrorCode.INVALID_PATH, ex.code());
    }
}
