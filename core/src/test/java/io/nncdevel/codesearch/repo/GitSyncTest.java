package io.nncdevel.codesearch.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.testsupport.GitFixture;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("requires_git")
class GitSyncTest {

    private RepositoryConfig repo(String id, Path remote) {
        return new RepositoryConfig(
                id, remote.toString(), "main", Hosting.GITHUB, "https://github.com/x/" + id, 900, List.of(), null);
    }

    @Test
    void clonesThenFetchesUpdates(@TempDir Path base) throws Exception {
        Path remote = GitFixture.createBareRemote(base, Map.of("README.md", "hello\n", "src/A.java", "class A {}\n"));
        Path ws = base.resolve("ws");
        RepositoryManager manager = new RepositoryManager(ws, List.of(repo("r1", remote)), SecretsFile.empty());
        GitSync sync = new GitSync(new CommandRunner());

        sync.syncAll(manager);

        RepositoryStatus status = manager.status("r1");
        assertEquals(SyncState.READY, status.state());
        assertEquals(SyncOutcome.SUCCESS, status.lastOutcome());
        assertNotNull(status.lastCommit());
        assertNotNull(status.lastSyncAt());
        assertTrue(Files.exists(ws.resolve("r1/README.md")));

        String firstCommit = status.lastCommit();
        GitFixture.commitToRemote(base, remote, Map.of("src/B.java", "class B {}\n"));
        sync.syncAll(manager);

        RepositoryStatus updated = manager.status("r1");
        assertEquals(SyncState.READY, updated.state());
        assertTrue(Files.exists(ws.resolve("r1/src/B.java")));
        assertTrue(!firstCommit.equals(updated.lastCommit()), "commit advanced after fetch");
    }

    @Test
    void perRepositoryFailureIsIsolated(@TempDir Path base) throws Exception {
        Path remote = GitFixture.createBareRemote(base, Map.of("README.md", "hi\n"));
        Path ws = base.resolve("ws");
        RepositoryManager manager = new RepositoryManager(
                ws,
                List.of(repo("good", remote), repo("bad", base.resolve("does-not-exist.git"))),
                SecretsFile.empty());

        new GitSync(new CommandRunner()).syncAll(manager);

        assertEquals(SyncState.READY, manager.status("good").state());
        assertEquals(SyncState.FAILED, manager.status("bad").state());
        assertEquals(SyncOutcome.FAILURE, manager.status("bad").lastOutcome());
        assertNotNull(manager.status("bad").lastError());
    }

    @Test
    void refreshStatesFromDiskMarksClonedRepoReady(@TempDir Path base) throws Exception {
        Path remote = GitFixture.createBareRemote(base, Map.of("README.md", "hi\n"));
        Path ws = base.resolve("ws");
        RepositoryManager manager = new RepositoryManager(ws, List.of(repo("r1", remote)), SecretsFile.empty());
        new GitSync(new CommandRunner()).syncAll(manager);

        // Fresh manager simulating a restart: no in-memory status yet.
        RepositoryManager restarted = new RepositoryManager(ws, List.of(repo("r1", remote)), SecretsFile.empty());
        assertEquals(SyncState.UNINITIALIZED, restarted.status("r1").state());
        restarted.refreshStatesFromDisk(new CommandRunner());
        assertEquals(SyncState.READY, restarted.status("r1").state());
        assertNotNull(restarted.status("r1").lastCommit());
    }
}
