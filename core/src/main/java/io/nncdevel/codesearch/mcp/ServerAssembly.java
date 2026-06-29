package io.nncdevel.codesearch.mcp;

import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.backend.GitLsBackend;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.config.ConfigLoader;
import io.nncdevel.codesearch.config.RepositoriesFile;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.obs.StructuredLogger;
import io.nncdevel.codesearch.repo.GitSync;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.tool.ToolContext;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Framework-agnostic composition of the core components from resolved configuration (design §3).
 * Both the standalone runtime and the Spring Boot starter use this to obtain a {@link
 * CapabilityProvider}; each then attaches it to an MCP server of its chosen transport.
 */
public final class ServerAssembly implements AutoCloseable {

    private final RepositoryManager repositoryManager;
    private final GitSync gitSync;
    private final CapabilityProvider capabilities;
    private final ExecutionGuard guard;
    private final StructuredLogger logger;

    private ServerAssembly(
            RepositoryManager repositoryManager,
            GitSync gitSync,
            CapabilityProvider capabilities,
            ExecutionGuard guard,
            StructuredLogger logger) {
        this.repositoryManager = repositoryManager;
        this.gitSync = gitSync;
        this.capabilities = capabilities;
        this.guard = guard;
        this.logger = logger;
    }

    /**
     * Loads configuration and wires the components, deriving readiness from existing workspaces so
     * search is available before any sync runs (design §5 availability).
     *
     * @param reposPath the resolved repos.json path
     * @param secretsPath the resolved secrets.json path, or {@code null} for anonymous access
     * @param workspaceRoot the resolved workspace root
     * @return the assembled components
     */
    public static ServerAssembly load(Path reposPath, Path secretsPath, Path workspaceRoot) {
        RepositoriesFile repos = ConfigLoader.loadRepositories(reposPath);
        Set<String> ids =
                repos.repositories().stream().map(RepositoryConfig::id).collect(Collectors.toSet());
        SecretsFile secrets = ConfigLoader.loadSecrets(secretsPath, ids);

        CommandRunner runner = new CommandRunner();
        RepositoryManager manager = new RepositoryManager(workspaceRoot, repos.repositories(), secrets);
        manager.refreshStatesFromDisk(runner);

        ToolContext context = new ToolContext(manager, new RipgrepBackend(runner), new GitLsBackend(runner));
        ExecutionGuard guard = new ExecutionGuard();
        StructuredLogger logger = StructuredLogger.forCategory("io.nncdevel.codesearch");
        CapabilityProvider capabilities = new CapabilityProvider(context, guard, logger);
        return new ServerAssembly(manager, new GitSync(runner), capabilities, guard, logger);
    }

    /**
     * Returns the capability provider (tool and resource specifications).
     *
     * @return the capability provider
     */
    public CapabilityProvider capabilities() {
        return capabilities;
    }

    /**
     * Returns the repository manager.
     *
     * @return the repository manager
     */
    public RepositoryManager repositoryManager() {
        return repositoryManager;
    }

    /**
     * Returns the git sync engine.
     *
     * @return the git sync engine
     */
    public GitSync gitSync() {
        return gitSync;
    }

    /**
     * Returns the structured logger.
     *
     * @return the structured logger
     */
    public StructuredLogger logger() {
        return logger;
    }

    /** Releases the execution guard's resources. */
    @Override
    public void close() {
        guard.close();
    }
}
