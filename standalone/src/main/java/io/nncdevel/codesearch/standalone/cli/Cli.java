package io.nncdevel.codesearch.standalone.cli;

import io.nncdevel.codesearch.config.ConfigLoader;
import io.nncdevel.codesearch.config.RepositoriesFile;
import java.nio.file.Path;

/**
 * Shared CLI configuration resolution honoring CLI &gt; environment variable &gt; file &gt; default
 * (spec §7).
 */
final class Cli {

    static final String ENV_REPOS = "CODE_SEARCH_REPOS_PATH";
    static final String ENV_SECRETS = "CODE_SEARCH_SECRETS_PATH";
    static final String ENV_WORKSPACE = "CODE_SEARCH_WORKSPACE_ROOT";
    static final String ENV_CONFIG = "CODE_SEARCH_CONFIG_PATH";

    private Cli() {}

    static Path workingDir() {
        return Path.of("").toAbsolutePath();
    }

    /** Resolves repos.json or returns {@code null} if none is configured. */
    static Path resolveReposPath(String cli) {
        return ConfigLoader.resolveReposPath(cli, System.getenv(ENV_REPOS), workingDir());
    }

    static Path resolveSecretsPath(String cli, RepositoriesFile repos) {
        return ConfigLoader.resolveSecretsPath(cli, System.getenv(ENV_SECRETS), repos.secretsPath(), workingDir());
    }

    static Path resolveWorkspaceRoot(String cli, RepositoriesFile repos) {
        return ConfigLoader.resolveWorkspaceRoot(
                cli, System.getenv(ENV_WORKSPACE), repos.workspaceRoot(), workingDir());
    }

    static Path resolveServerConfigPath(String cli) {
        return ConfigLoader.discoverServerConfigPath(cli, System.getenv(ENV_CONFIG), workingDir());
    }
}
