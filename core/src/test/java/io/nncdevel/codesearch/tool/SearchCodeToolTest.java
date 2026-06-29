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
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("requires_git")
@Tag("requires_rg")
class SearchCodeToolTest {

    private SearchCodeTool tool(Path base, List<String> excludePaths) throws Exception {
        Path ws = base.resolve("ws");
        Path repoDir = ws.resolve("r1");
        Files.createDirectories(repoDir);
        GitFixture.git(repoDir, "init", "-b", "main");
        GitFixture.git(repoDir, "config", "user.email", "t@example.com");
        GitFixture.git(repoDir, "config", "user.name", "T");
        write(repoDir, "src/A.java", "class A {}\n// TODO a\n");
        write(repoDir, "src/B.java", "class B {}\n// TODO b\n// TODO bb\n");
        write(repoDir, "vendor/V.java", "// TODO vendor\n");
        GitFixture.git(repoDir, "add", "-A");
        GitFixture.git(repoDir, "commit", "-m", "init");
        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, excludePaths, null);
        RepositoryManager m = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        m.refreshStatesFromDisk(new CommandRunner());
        CommandRunner runner = new CommandRunner();
        return new SearchCodeTool(new ToolContext(m, new RipgrepBackend(runner), new GitLsBackend(runner)));
    }

    private static void write(Path root, String rel, String content) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @Test
    void contentModeExcludesVendorAndCountsMatches(@TempDir Path base) throws Exception {
        SearchCodeTool tool = tool(base, List.of("vendor/"));
        SearchResult.Content r = (SearchResult.Content)
                tool.search(new SearchRequest("TODO", "r1", null, null, null, false, OutputMode.CONTENT, 0, 0, 50));
        assertEquals(3, r.totalMatches());
        assertFalse(r.truncated());
        assertTrue(r.matches().stream().noneMatch(m -> m.filePath().startsWith("vendor/")));
        assertTrue(r.matches().stream()
                .anyMatch(m -> m.gitUrl().equals("https://github.com/x/r1/blob/main/src/B.java#L2")));
    }

    @Test
    void globFilterRestrictsFiles(@TempDir Path base) throws Exception {
        SearchCodeTool tool = tool(base, List.of());
        SearchResult.Content r = (SearchResult.Content) tool.search(
                new SearchRequest("TODO", "r1", null, "src/A.java", null, false, OutputMode.CONTENT, 0, 0, 50));
        assertEquals(1, r.totalMatches());
        assertEquals("src/A.java", r.matches().get(0).filePath());
    }

    @Test
    void filesWithMatchesModeReturnsDistinctFiles(@TempDir Path base) throws Exception {
        SearchCodeTool tool = tool(base, List.of("vendor/"));
        SearchResult.Files r = (SearchResult.Files) tool.search(
                new SearchRequest("TODO", "r1", null, null, null, false, OutputMode.FILES_WITH_MATCHES, 0, 0, 50));
        assertEquals(2, r.files().size());
        assertTrue(r.files().stream().anyMatch(f -> f.gitUrl().equals("https://github.com/x/r1/blob/main/src/B.java")));
    }

    @Test
    void countModeReportsPerFileCounts(@TempDir Path base) throws Exception {
        SearchCodeTool tool = tool(base, List.of("vendor/"));
        SearchResult.Count r = (SearchResult.Count)
                tool.search(new SearchRequest("TODO", "r1", null, null, null, false, OutputMode.COUNT, 0, 0, 50));
        int bCount = r.files().stream()
                .filter(c -> c.filePath().equals("src/B.java"))
                .findFirst()
                .orElseThrow()
                .matchCount();
        assertEquals(2, bCount);
    }

    @Test
    void maxResultsTruncates(@TempDir Path base) throws Exception {
        SearchCodeTool tool = tool(base, List.of("vendor/"));
        SearchResult.Content r = (SearchResult.Content)
                tool.search(new SearchRequest("TODO", "r1", null, null, null, false, OutputMode.CONTENT, 0, 0, 1));
        assertEquals(1, r.matches().size());
        assertTrue(r.truncated());
        assertEquals(3, r.totalMatches());
    }

    @Test
    void contextProducesRangeUrl(@TempDir Path base) throws Exception {
        SearchCodeTool tool = tool(base, List.of());
        SearchResult.Content r = (SearchResult.Content)
                tool.search(new SearchRequest("class B", "r1", null, null, null, false, OutputMode.CONTENT, 0, 1, 50));
        SearchMatch m = r.matches().get(0);
        assertEquals(1, m.contextAfter().size());
        assertEquals("https://github.com/x/r1/blob/main/src/B.java#L1-L2", m.gitUrl());
    }
}
