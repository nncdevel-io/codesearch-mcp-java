package io.nncdevel.codesearch.tool;

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

/**
 * Sanity smoke for {@code search_code} latency under the requirements' scale assumptions (a
 * moderate repository, not the full 1M-line ceiling). Asserts a generous upper bound so it is a
 * regression tripwire, not a benchmark.
 */
@Tag("requires_git")
@Tag("requires_rg")
@Tag("perf")
class SearchCodePerfSmokeTest {

    private static final int FILES = 400;
    private static final int LINES_PER_FILE = 60;

    @Test
    void searchCompletesWellUnderTimeout(@TempDir Path base) throws Exception {
        Path ws = base.resolve("ws");
        Path repoDir = ws.resolve("r1");
        Files.createDirectories(repoDir.resolve("src"));
        GitFixture.git(repoDir, "init", "-b", "main");
        GitFixture.git(repoDir, "config", "user.email", "t@example.com");
        GitFixture.git(repoDir, "config", "user.name", "T");
        for (int f = 0; f < FILES; f++) {
            StringBuilder sb = new StringBuilder();
            for (int l = 0; l < LINES_PER_FILE; l++) {
                sb.append("public void method").append(l).append("() { /* filler */ }\n");
            }
            sb.append("// NEEDLE marker in file ").append(f).append('\n');
            Files.writeString(repoDir.resolve("src/File" + f + ".java"), sb.toString());
        }
        GitFixture.git(repoDir, "add", "-A");
        GitFixture.git(repoDir, "commit", "-m", "init");

        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, List.of(), null);
        RepositoryManager manager = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        manager.refreshStatesFromDisk(new CommandRunner());
        CommandRunner runner = new CommandRunner();
        SearchCodeTool tool =
                new SearchCodeTool(new ToolContext(manager, new RipgrepBackend(runner), new GitLsBackend(runner)));

        long start = System.nanoTime();
        SearchResult.Content result = (SearchResult.Content)
                tool.search(new SearchRequest("NEEDLE", "r1", null, null, null, false, OutputMode.CONTENT, 0, 0, 500));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(result.totalMatches() >= FILES, "expected a match per file: " + result.totalMatches());
        assertTrue(elapsedMs < 2000, "search_code smoke exceeded 2s: " + elapsedMs + "ms");
    }
}
