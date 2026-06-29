package io.nncdevel.codesearch.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.backend.GitLsBackend;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.testsupport.GitFixture;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("requires_git")
@Tag("requires_rg")
class ListFilesToolTest {

    private Path ws;

    private ListFilesTool tool(Path base, List<String> excludePaths) throws Exception {
        ws = base.resolve("ws");
        Path repoDir = ws.resolve("r1");
        Files.createDirectories(repoDir);
        GitFixture.git(repoDir, "init", "-b", "main");
        GitFixture.git(repoDir, "config", "user.email", "t@example.com");
        GitFixture.git(repoDir, "config", "user.name", "T");
        write(repoDir, "src/A.java", "class A {}\n");
        write(repoDir, "src/B.java", "class B {}\n");
        write(repoDir, ".gitignore", "ignored/\n");
        write(repoDir, "ignored/X.java", "class X {}\n");
        write(repoDir, "vendor/V.java", "class V {}\n");
        GitFixture.git(repoDir, "add", "-A");
        GitFixture.git(repoDir, "commit", "-m", "init");
        // Make B newer than A.
        Files.setLastModifiedTime(repoDir.resolve("src/A.java"), FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        Files.setLastModifiedTime(repoDir.resolve("src/B.java"), FileTime.from(Instant.parse("2026-02-01T00:00:00Z")));
        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, excludePaths, null);
        RepositoryManager m = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        m.refreshStatesFromDisk(new CommandRunner());
        CommandRunner runner = new CommandRunner();
        return new ListFilesTool(new ToolContext(m, new RipgrepBackend(runner), new GitLsBackend(runner)));
    }

    private static void write(Path root, String rel, String content) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @Test
    void listsIncludingGitignoredButExcludingConfiguredPrefix(@TempDir Path base) throws Exception {
        ListFilesTool tool = tool(base, List.of("vendor/"));
        ListFilesResult r = tool.list("r1", "**/*.java", null, 50);
        List<String> paths = r.files().stream().map(FileEntry::filePath).toList();
        assertTrue(paths.contains("ignored/X.java"), "gitignored files are included");
        assertTrue(paths.contains("src/A.java"));
        assertFalse(paths.stream().anyMatch(p -> p.startsWith("vendor/")), "exclude_paths filtered");
    }

    @Test
    void ordersByLastModifiedDescending(@TempDir Path base) throws Exception {
        ListFilesTool tool = tool(base, List.of("vendor/", "ignored/"));
        ListFilesResult r = tool.list("r1", "src/*.java", null, 50);
        assertEquals("src/B.java", r.files().get(0).filePath());
        assertEquals("src/A.java", r.files().get(1).filePath());
    }

    @Test
    void truncatesToMaxResults(@TempDir Path base) throws Exception {
        ListFilesTool tool = tool(base, List.of());
        ListFilesResult r = tool.list("r1", "**/*.java", null, 1);
        assertEquals(1, r.files().size());
        assertTrue(r.truncated());
    }

    @Test
    void producesFileLevelGitUrl(@TempDir Path base) throws Exception {
        ListFilesTool tool = tool(base, List.of("vendor/", "ignored/"));
        ListFilesResult r = tool.list("r1", "src/A.java", null, 50);
        assertEquals(
                "https://github.com/x/r1/blob/main/src/A.java", r.files().get(0).gitUrl());
    }
}
