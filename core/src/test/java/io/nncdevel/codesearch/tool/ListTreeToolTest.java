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
class ListTreeToolTest {

    private ListTreeTool tool(Path base) throws Exception {
        Path ws = base.resolve("ws");
        Path repoDir = ws.resolve("r1");
        Files.createDirectories(repoDir);
        GitFixture.git(repoDir, "init", "-b", "main");
        GitFixture.git(repoDir, "config", "user.email", "t@example.com");
        GitFixture.git(repoDir, "config", "user.name", "T");
        write(repoDir, "src/main/java/App.java", "class App {}\n");
        write(repoDir, "src/main/resources/x.txt", "x\n");
        write(repoDir, "src/test/java/AppTest.java", "class AppTest {}\n");
        write(repoDir, "README.md", "# r1\n");
        write(repoDir, "vendor/V.java", "class V {}\n");
        GitFixture.git(repoDir, "add", "-A");
        GitFixture.git(repoDir, "commit", "-m", "init");
        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, List.of("vendor/"), null);
        RepositoryManager m = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        m.refreshStatesFromDisk(new CommandRunner());
        CommandRunner runner = new CommandRunner();
        return new ListTreeTool(new ToolContext(m, new RipgrepBackend(runner), new GitLsBackend(runner)));
    }

    private static void write(Path root, String rel, String content) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }

    @Test
    void rendersDepthLimitedTreeExcludingVendor(@TempDir Path base) throws Exception {
        ListTreeResult r = tool(base).list("r1", "", 2, true, 200);
        String expected = String.join("\n", "README.md", "src/", "├── main/", "└── test/");
        assertEquals(expected, r.tree());
        assertEquals("", r.rootPath());
        assertFalse(r.truncated());
        assertEquals(4, r.entryCount());
        assertFalse(r.tree().contains("vendor"));
    }

    @Test
    void rootsAtSubPath(@TempDir Path base) throws Exception {
        ListTreeResult r = tool(base).list("r1", "src", 2, true, 200);
        String expected = String.join("\n", "main/", "├── java/", "└── resources/", "test/", "└── java/");
        assertEquals(expected, r.tree());
        assertEquals("src", r.rootPath());
    }

    @Test
    void showFilesFalseHidesFiles(@TempDir Path base) throws Exception {
        ListTreeResult r = tool(base).list("r1", "", 3, false, 200);
        assertFalse(r.tree().contains("README.md"));
        assertTrue(r.tree().contains("src/"));
    }
}
