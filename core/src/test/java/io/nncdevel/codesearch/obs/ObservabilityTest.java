package io.nncdevel.codesearch.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservabilityTest {

    @Test
    void snapshotReportsUninitializedStatus(@TempDir Path ws) {
        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, List.of(), null);
        RepositoryManager m = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());

        List<Map<String, Object>> snapshot = Observability.snapshot(m);

        assertEquals(1, snapshot.size());
        Map<String, Object> entry = snapshot.get(0);
        assertEquals("r1", entry.get("repository"));
        assertEquals("uninitialized", entry.get("state"));
        assertNull(entry.get("last_sync_at"));
        assertNull(entry.get("last_outcome"));
    }

    @Test
    void structuredLoggerEmitsWithoutError(@TempDir Path ws) {
        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, List.of(), null);
        RepositoryManager m = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        StructuredLogger logger = StructuredLogger.forCategory("test.obs");
        Observability.logStatus(logger, m);
        logger.info("startup", Map.of("token", "should-be-masked", "repos", 1));
    }
}
