package io.nncdevel.codesearch.repo;

import io.nncdevel.codesearch.backend.CommandResult;
import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretEntry;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the configured repositories, their workspace paths, and their sync status. Status is
 * mutable and thread-safe; configuration is immutable (design §3).
 */
public final class RepositoryManager {

    private final Path workspaceRoot;
    private final Map<String, RepositoryConfig> repositories = new LinkedHashMap<>();
    private final SecretsFile secrets;
    private final ConcurrentHashMap<String, RepositoryStatus> statuses = new ConcurrentHashMap<>();

    /**
     * Constructs a manager over the given repositories, initializing each status to {@link
     * SyncState#UNINITIALIZED}.
     *
     * @param workspaceRoot the directory under which each repository is cloned
     * @param repositories the configured repositories, in catalog order
     * @param secrets the credentials keyed by repository id
     */
    public RepositoryManager(Path workspaceRoot, List<RepositoryConfig> repositories, SecretsFile secrets) {
        this.workspaceRoot = workspaceRoot;
        this.secrets = secrets;
        for (RepositoryConfig repo : repositories) {
            this.repositories.put(repo.id(), repo);
            this.statuses.put(repo.id(), RepositoryStatus.uninitialized(repo.id()));
        }
    }

    /**
     * Returns the root directory under which repositories are cloned.
     *
     * @return the workspace root path
     */
    public Path workspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Returns the (not necessarily existing) workspace directory for a repository.
     *
     * @param id the repository id
     * @return the workspace path {@code workspaceRoot/id}
     */
    public Path workspacePath(String id) {
        return workspaceRoot.resolve(id);
    }

    /**
     * Returns the configured repositories in catalog order.
     *
     * @return a new list of repository configurations
     */
    public List<RepositoryConfig> all() {
        return new ArrayList<>(repositories.values());
    }

    /**
     * Returns the configuration for a repository.
     *
     * @param id the repository id
     * @return the matching configuration
     * @throws ToolException with {@link ErrorCode#REPO_NOT_FOUND} if {@code id} is not configured
     */
    public RepositoryConfig require(String id) {
        RepositoryConfig repo = repositories.get(id);
        if (repo == null) {
            throw new ToolException(
                    ErrorCode.REPO_NOT_FOUND, "Repository '" + id + "' is not configured", Map.of("repository", id));
        }
        return repo;
    }

    /**
     * Returns the credentials for a repository.
     *
     * @param id the repository id
     * @return the secret entry, or {@link SecretEntry#none()} if none is configured
     */
    public SecretEntry secretFor(String id) {
        return secrets.forRepository(id);
    }

    /**
     * Returns the current status of a repository.
     *
     * @param id the repository id
     * @return the current status, or an uninitialized status if unknown
     */
    public RepositoryStatus status(String id) {
        return statuses.getOrDefault(id, RepositoryStatus.uninitialized(id));
    }

    /**
     * Returns the status of every repository in catalog order.
     *
     * @return a new list of statuses
     */
    public List<RepositoryStatus> statuses() {
        List<RepositoryStatus> result = new ArrayList<>();
        for (String id : repositories.keySet()) {
            result.add(status(id));
        }
        return result;
    }

    /**
     * Replaces the stored status for a repository.
     *
     * @param id the repository id
     * @param status the new status
     */
    public void updateStatus(String id, RepositoryStatus status) {
        statuses.put(id, status);
    }

    /**
     * Resolves the workspace of a ready repository for tool use.
     *
     * @param id the repository id
     * @return the workspace directory of the ready repository
     * @throws ToolException with {@link ErrorCode#REPO_NOT_FOUND} if unconfigured, or {@link
     *     ErrorCode#REPO_NOT_READY} if its initial clone has not completed
     */
    public Path requireReadyWorkspace(String id) {
        require(id);
        Path ws = workspacePath(id);
        if (status(id).state() != SyncState.READY || !Files.isDirectory(ws.resolve(".git"))) {
            throw new ToolException(
                    ErrorCode.REPO_NOT_READY, "Repository '" + id + "' is not ready", Map.of("repository", id));
        }
        return ws;
    }

    /**
     * Reads each repository's {@code .git} directly to derive readiness and {@code last_commit} on
     * startup, so search can be served before any sync runs (design §5 availability).
     *
     * @param runner the command runner used to read the current commit
     */
    public void refreshStatesFromDisk(CommandRunner runner) {
        for (RepositoryConfig repo : repositories.values()) {
            Path ws = workspacePath(repo.id());
            if (!Files.isDirectory(ws.resolve(".git"))) {
                continue;
            }
            String commit = headCommit(runner, ws);
            RepositoryStatus current = status(repo.id());
            statuses.put(
                    repo.id(),
                    new RepositoryStatus(
                            repo.id(),
                            SyncState.READY,
                            current.lastSyncAt(),
                            current.lastOutcome(),
                            current.lastError(),
                            commit));
        }
    }

    private static String headCommit(CommandRunner runner, Path ws) {
        CommandResult r = runner.run(List.of("git", "rev-parse", "HEAD"), ws, Map.of(), Duration.ofSeconds(10));
        return r.succeeded() ? r.stdout().trim() : null;
    }
}
