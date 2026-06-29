package io.nncdevel.codesearch.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.backend.GitLsBackend;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.testsupport.GitFixture;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("requires_git")
class ReadFileToolTest {

    private ToolContext ctx(RepositoryManager m) {
        CommandRunner runner = new CommandRunner();
        return new ToolContext(m, new RipgrepBackend(runner), new GitLsBackend(runner));
    }

    private RepositoryConfig repoConfig() {
        return new RepositoryConfig("r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, List.of(), null);
    }

    private RepositoryManager readyManager(Path base) throws Exception {
        Path ws = base.resolve("ws");
        Path repoDir = ws.resolve("r1");
        Files.createDirectories(repoDir);
        GitFixture.git(repoDir, "init", "-b", "main");
        GitFixture.git(repoDir, "config", "user.email", "t@example.com");
        GitFixture.git(repoDir, "config", "user.name", "T");
        Files.writeString(repoDir.resolve("App.java"), "line1\nline2\nline3\nline4\n");
        GitFixture.git(repoDir, "add", "-A");
        GitFixture.git(repoDir, "commit", "-m", "init");
        RepositoryManager m = new RepositoryManager(ws, List.of(repoConfig()), SecretsFile.empty());
        m.refreshStatesFromDisk(new CommandRunner());
        return m;
    }

    @Test
    void readsRangeWithFormattingAndRangeUrl(@TempDir Path base) throws Exception {
        ReadFileTool tool = new ReadFileTool(ctx(readyManager(base)));
        ReadFileResult r = tool.read("r1", "App.java", 2, 2);
        assertEquals(2, r.startLine());
        assertEquals(3, r.endLine());
        assertEquals(4, r.totalLines());
        assertEquals("2\tline2\n3\tline3", r.content());
        assertEquals("https://github.com/x/r1/blob/main/App.java#L2-L3", r.gitUrl());
    }

    @Test
    void singleLineUsesSingleAnchor(@TempDir Path base) throws Exception {
        ReadFileTool tool = new ReadFileTool(ctx(readyManager(base)));
        ReadFileResult r = tool.read("r1", "App.java", 1, 1);
        assertEquals("https://github.com/x/r1/blob/main/App.java#L1", r.gitUrl());
        assertEquals("1\tline1", r.content());
    }

    @Test
    void binaryFileRejected(@TempDir Path base) throws Exception {
        RepositoryManager m = readyManager(base);
        Files.write(base.resolve("ws/r1/bin.dat"), new byte[] {0x41, 0x00, 0x42});
        ToolException ex =
                assertThrows(ToolException.class, () -> new ReadFileTool(ctx(m)).read("r1", "bin.dat", 1, 10));
        assertEquals(ErrorCode.FILE_BINARY, ex.code());
    }

    @Test
    void tooLargeRejected(@TempDir Path base) throws Exception {
        RepositoryManager m = readyManager(base);
        byte[] big = new byte[(int) ReadFileTool.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(big, (byte) 'a');
        Files.write(base.resolve("ws/r1/big.txt"), big);
        ToolException ex =
                assertThrows(ToolException.class, () -> new ReadFileTool(ctx(m)).read("r1", "big.txt", 1, 10));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.code());
    }

    @Test
    void traversalRejected(@TempDir Path base) throws Exception {
        RepositoryManager m = readyManager(base);
        ToolException ex =
                assertThrows(ToolException.class, () -> new ReadFileTool(ctx(m)).read("r1", "../escape", 1, 10));
        assertEquals(ErrorCode.INVALID_PATH, ex.code());
    }

    @Test
    void missingFileRejected(@TempDir Path base) throws Exception {
        RepositoryManager m = readyManager(base);
        ToolException ex =
                assertThrows(ToolException.class, () -> new ReadFileTool(ctx(m)).read("r1", "nope.txt", 1, 10));
        assertEquals(ErrorCode.PATH_NOT_FOUND, ex.code());
    }

    @Test
    void unreadyRepositoryRejected(@TempDir Path base) {
        RepositoryManager m = new RepositoryManager(base.resolve("ws"), List.of(repoConfig()), SecretsFile.empty());
        ToolException ex =
                assertThrows(ToolException.class, () -> new ReadFileTool(ctx(m)).read("r1", "App.java", 1, 10));
        assertEquals(ErrorCode.REPO_NOT_READY, ex.code());
    }

    @Test
    void readBeyondEofYieldsEmpty(@TempDir Path base) throws Exception {
        ReadFileResult r = new ReadFileTool(ctx(readyManager(base))).read("r1", "App.java", 99, 10);
        assertTrue(r.content().isEmpty());
        assertEquals(4, r.totalLines());
    }
}
