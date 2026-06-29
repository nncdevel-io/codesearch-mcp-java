package io.nncdevel.codesearch.repo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.nncdevel.codesearch.config.AuthType;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretEntry;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryManagerTest {

    private RepositoryConfig repo(String id) {
        return new RepositoryConfig(
                id, "git@host:/" + id, "main", Hosting.GITHUB, "https://github.com/x/" + id, 900, List.of(), null);
    }

    @Test
    void requireUnknownThrowsRepoNotFound(@TempDir Path ws) {
        RepositoryManager m =
                new RepositoryManager(ws, List.of(repo("a")), io.nncdevel.codesearch.config.SecretsFile.empty());
        ToolException ex = assertThrows(ToolException.class, () -> m.require("missing"));
        assertEquals(ErrorCode.REPO_NOT_FOUND, ex.code());
    }

    @Test
    void requireReadyOnUninitializedThrowsRepoNotReady(@TempDir Path ws) {
        RepositoryManager m =
                new RepositoryManager(ws, List.of(repo("a")), io.nncdevel.codesearch.config.SecretsFile.empty());
        ToolException ex = assertThrows(ToolException.class, () -> m.requireReadyWorkspace("a"));
        assertEquals(ErrorCode.REPO_NOT_READY, ex.code());
    }

    @Test
    void preservesConfigOrder(@TempDir Path ws) {
        RepositoryManager m = new RepositoryManager(
                ws, List.of(repo("z"), repo("a"), repo("m")), io.nncdevel.codesearch.config.SecretsFile.empty());
        assertEquals(
                List.of("z", "a", "m"),
                m.all().stream().map(RepositoryConfig::id).toList());
    }

    @Test
    void tokenRemoteGetsUsernameInjected() {
        SecretEntry token = new SecretEntry(AuthType.TOKEN, "ghp_x", null);
        assertEquals(
                "https://x-access-token@github.com/x/r.git",
                GitSync.authenticatedRemote("https://github.com/x/r.git", token));
    }

    @Test
    void sshRemoteUnchanged() {
        SecretEntry token = new SecretEntry(AuthType.TOKEN, "ghp_x", null);
        assertEquals("git@github.com:x/r.git", GitSync.authenticatedRemote("git@github.com:x/r.git", token));
    }

    @Test
    void noneAuthLeavesRemoteUnchanged() {
        assertEquals(
                "https://github.com/x/r.git",
                GitSync.authenticatedRemote("https://github.com/x/r.git", SecretEntry.none()));
    }
}
