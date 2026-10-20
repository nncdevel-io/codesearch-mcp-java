package io.nncdevel.codesearch.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.obs.StructuredLogger;
import io.nncdevel.codesearch.testsupport.GitFixture;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("requires_git")
class SyncSchedulerTest {

    @Test
    void runSyncClonesAndUpdatesStatus(@TempDir Path base) throws Exception {
        Path remote = GitFixture.createBareRemote(base, Map.of("README.md", "hi\n"));
        Path ws = base.resolve("ws");
        RepositoryConfig repo = new RepositoryConfig(
                "r1", remote.toString(), "main", Hosting.GITHUB, "https://github.com/x/r1", 60, List.of(), null);
        RepositoryManager manager = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());

        try (SyncScheduler scheduler = new SyncScheduler(
                new GitSync(new CommandRunner()), manager, StructuredLogger.forCategory("test.scheduler"))) {
            scheduler.runSync(repo);
        }

        assertEquals(SyncState.READY, manager.status("r1").state());
        assertEquals(SyncOutcome.SUCCESS, manager.status("r1").lastOutcome());
        assertNotNull(manager.status("r1").lastCommit());
    }

    @Test
    void startAndCloseAreSafe(@TempDir Path base) throws Exception {
        Path remote = GitFixture.createBareRemote(base, Map.of("README.md", "hi\n"));
        Path ws = base.resolve("ws");
        RepositoryConfig repo = new RepositoryConfig(
                "r1", remote.toString(), "main", Hosting.GITHUB, "https://github.com/x/r1", 3600, List.of(), null);
        RepositoryManager manager = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        SyncScheduler scheduler = new SyncScheduler(
                new GitSync(new CommandRunner()), manager, StructuredLogger.forCategory("test.scheduler"));
        scheduler.start();
        scheduler.close();
    }
}
