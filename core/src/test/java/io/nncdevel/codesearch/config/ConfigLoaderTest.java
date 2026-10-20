package io.nncdevel.codesearch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nncdevel.codesearch.util.Hosting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    private static Path write(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    @Test
    void loadsValidRepositories(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", """
            {
              "workspace_root": "/var/ws",
              "secrets": "/etc/secrets.json",
              "repositories": [
                {
                  "id": "main-app",
                  "remote": "git@github.com:example/main-app.git",
                  "branch": "main",
                  "hosting": "github",
                  "hosting_base_url": "https://github.com/example/main-app",
                  "refresh_interval_seconds": 900,
                  "exclude_paths": ["docs/generated/", "vendor/"],
                  "description": "Backend service"
                }
              ]
            }
            """);
        RepositoriesFile file = ConfigLoader.loadRepositories(p);
        assertEquals("/var/ws", file.workspaceRoot());
        assertEquals("/etc/secrets.json", file.secretsPath());
        assertEquals(1, file.repositories().size());
        RepositoryConfig repo = file.repositories().get(0);
        assertEquals("main-app", repo.id());
        assertEquals(Hosting.GITHUB, repo.hosting());
        assertEquals(900, repo.refreshIntervalSeconds());
        assertEquals(2, repo.excludePaths().size());
        assertEquals("Backend service", repo.description());
    }

    @Test
    void defaultsRefreshWhenAbsent(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", """
            {"repositories": [
              {"id": "r1", "remote": "x", "branch": "main",
               "hosting": "gitlab", "hosting_base_url": "https://gitlab.com/x/r1"}
            ]}
            """);
        RepositoryConfig repo = ConfigLoader.loadRepositories(p).repositories().get(0);
        assertEquals(900, repo.refreshIntervalSeconds());
        assertTrue(repo.excludePaths().isEmpty());
        assertNull(repo.description());
    }

    @Test
    void rejectsMissingRequiredField(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", """
            {"repositories": [
              {"id": "r1", "branch": "main", "hosting": "github", "hosting_base_url": "u"}
            ]}
            """);
        assertThrows(ConfigException.class, () -> ConfigLoader.loadRepositories(p));
    }

    @Test
    void rejectsDuplicateId(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", """
            {"repositories": [
              {"id": "dup", "remote": "x", "branch": "main", "hosting": "github", "hosting_base_url": "u"},
              {"id": "dup", "remote": "y", "branch": "main", "hosting": "github", "hosting_base_url": "u"}
            ]}
            """);
        ConfigException ex = assertThrows(ConfigException.class, () -> ConfigLoader.loadRepositories(p));
        assertTrue(ex.getMessage().contains("duplicate"));
    }

    @Test
    void rejectsInvalidHosting(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", """
            {"repositories": [
              {"id": "r1", "remote": "x", "branch": "main", "hosting": "svn", "hosting_base_url": "u"}
            ]}
            """);
        assertThrows(ConfigException.class, () -> ConfigLoader.loadRepositories(p));
    }

    @Test
    void rejectsLowRefresh(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", """
            {"repositories": [
              {"id": "r1", "remote": "x", "branch": "main", "hosting": "github",
               "hosting_base_url": "u", "refresh_interval_seconds": 10}
            ]}
            """);
        assertThrows(ConfigException.class, () -> ConfigLoader.loadRepositories(p));
    }

    @Test
    void rejectsEmptyRepositoryList(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", "{\"repositories\": []}");
        assertThrows(ConfigException.class, () -> ConfigLoader.loadRepositories(p));
    }

    @Test
    void rejectsInvalidJson(@TempDir Path dir) throws IOException {
        Path p = write(dir, "repos.json", "{ not json");
        assertThrows(ConfigException.class, () -> ConfigLoader.loadRepositories(p));
    }

    @Test
    void loadsTokenSecret(@TempDir Path dir) throws IOException {
        Path p = write(dir, "secrets.json", """
            {"secrets": {"main-app": {"auth_type": "token", "token": "ghp_xxx"}}}
            """);
        restrictPermissions(p);
        SecretsFile secrets = ConfigLoader.loadSecrets(p, Set.of("main-app"));
        SecretEntry entry = secrets.forRepository("main-app");
        assertEquals(AuthType.TOKEN, entry.authType());
        assertEquals("ghp_xxx", entry.token());
    }

    @Test
    void tokenSecretMissingTokenFails(@TempDir Path dir) throws IOException {
        Path p = write(dir, "secrets.json", "{\"secrets\": {\"r1\": {\"auth_type\": \"token\"}}}");
        restrictPermissions(p);
        assertThrows(ConfigException.class, () -> ConfigLoader.loadSecrets(p, Set.of("r1")));
    }

    @Test
    void orphanSecretIsIgnored(@TempDir Path dir) throws IOException {
        Path p = write(dir, "secrets.json", "{\"secrets\": {\"gone\": {\"auth_type\": \"token\", \"token\": \"x\"}}}");
        restrictPermissions(p);
        SecretsFile secrets = ConfigLoader.loadSecrets(p, Set.of("main-app"));
        assertEquals(AuthType.NONE, secrets.forRepository("gone").authType());
        assertTrue(secrets.byRepositoryId().isEmpty());
    }

    @Test
    void looseSecretPermissionsRejected(@TempDir Path dir) throws IOException {
        Path p = write(dir, "secrets.json", "{\"secrets\": {\"r1\": {\"auth_type\": \"none\"}}}");
        try {
            Files.setPosixFilePermissions(
                    p,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.OTHERS_READ));
        } catch (UnsupportedOperationException e) {
            return; // non-POSIX; skip
        }
        assertThrows(ConfigException.class, () -> ConfigLoader.loadSecrets(p, Set.of("r1")));
    }

    @Test
    void nullSecretsPathYieldsEmpty() {
        assertTrue(ConfigLoader.loadSecrets(null, Set.of()).byRepositoryId().isEmpty());
    }

    @Test
    void serverConfigDefaultsAndParse(@TempDir Path dir) throws IOException {
        assertEquals(ServerConfig.defaults(), ConfigLoader.loadServerConfig(null));
        Path p = write(dir, "server.json", """
            {"transport": "http", "host": "0.0.0.0", "port": 9000, "enable_scheduler": true}
            """);
        ServerConfig cfg = ConfigLoader.loadServerConfig(p);
        assertEquals(Transport.HTTP, cfg.transport());
        assertEquals("0.0.0.0", cfg.host());
        assertEquals(9000, cfg.port());
        assertTrue(cfg.enableScheduler());
    }

    @Test
    void serverConfigRejectsBadPort(@TempDir Path dir) throws IOException {
        Path p = write(dir, "server.json", "{\"port\": 70000}");
        assertThrows(ConfigException.class, () -> ConfigLoader.loadServerConfig(p));
    }

    @Test
    void resolvesReposPathPrecedence(@TempDir Path dir) throws IOException {
        assertEquals(
                Path.of("/cli/repos.json"), ConfigLoader.resolveReposPath("/cli/repos.json", "/env/repos.json", dir));
        assertEquals(Path.of("/env/repos.json"), ConfigLoader.resolveReposPath(null, "/env/repos.json", dir));
        assertNull(ConfigLoader.resolveReposPath(null, null, dir));
        Files.createDirectories(dir.resolve("config"));
        write(dir.resolve("config"), "repos.json", "{\"repositories\": []}");
        assertEquals(dir.resolve("config/repos.json"), ConfigLoader.resolveReposPath(null, null, dir));
    }

    @Test
    void resolvesWorkspaceRootDefault(@TempDir Path dir) {
        assertEquals(dir.resolve("workspaces"), ConfigLoader.resolveWorkspaceRoot(null, null, null, dir));
        assertEquals(Path.of("/ws"), ConfigLoader.resolveWorkspaceRoot(null, "/ws", null, dir));
    }

    private static void restrictPermissions(Path p) throws IOException {
        try {
            Files.setPosixFilePermissions(p, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException e) {
            // non-POSIX; tests that rely on this will no-op
        }
    }
}
