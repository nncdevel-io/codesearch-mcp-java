package io.nncdevel.codesearch.repo;

import io.nncdevel.codesearch.backend.CommandResult;
import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs {@code git clone --single-branch}, {@code fetch}, and {@code reset --hard} for a
 * repository, isolating per-repository failures so one repo's error never affects others (spec
 * §4.5, requirements §5.2). Authentication is applied via environment, never via command-line
 * arguments, so secrets never appear in the process table or logs.
 */
public final class GitSync {

    private static final Logger LOG = LoggerFactory.getLogger(GitSync.class);
    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(10);

    private final CommandRunner runner;

    /**
     * Constructs a sync component that runs git through the given runner.
     *
     * @param runner the command runner used to invoke git
     */
    public GitSync(CommandRunner runner) {
        this.runner = runner;
    }

    /**
     * Synchronizes every repository, updating each one's status independently. A failure on one
     * repository is recorded and does not abort the others.
     *
     * @param manager the repository manager whose repositories are synced and whose statuses are
     *     updated in place
     */
    public void syncAll(RepositoryManager manager) {
        for (RepositoryConfig repo : manager.all()) {
            RepositoryStatus updated;
            try {
                updated = syncOne(
                        repo,
                        manager.secretFor(repo.id()),
                        manager.workspacePath(repo.id()),
                        manager.status(repo.id()));
            } catch (RuntimeException e) {
                LOG.warn("sync_failed: repository={} error={}", repo.id(), e.getMessage());
                updated = manager.status(repo.id()).withFailure(e.getMessage());
            }
            manager.updateStatus(repo.id(), updated);
        }
    }

    /**
     * Synchronizes one repository and returns its new status (never throws for git failures).
     *
     * @param repo the repository to synchronize
     * @param secret the credentials to apply, or {@link SecretEntry#none()} for anonymous access
     * @param workspace the repository's workspace directory
     * @param current the repository's current status, used as the base for the returned status
     * @return the updated status reflecting success or failure
     */
    public RepositoryStatus syncOne(
            RepositoryConfig repo, SecretEntry secret, Path workspace, RepositoryStatus current) {
        Path tempAuthDir = null;
        try {
            Map<String, String> env = new HashMap<>();
            env.put("GIT_TERMINAL_PROMPT", "0");
            tempAuthDir = applyAuth(env, secret);

            boolean cloned = Files.isDirectory(workspace.resolve(".git"));
            CommandResult result = cloned ? fetchAndReset(repo, workspace, env) : clone(repo, workspace, env, secret);
            if (!result.succeeded()) {
                return current.withFailure(summarize(result));
            }
            String commit = headCommit(workspace);
            return current.withSuccess(Instant.now(), commit);
        } catch (IOException e) {
            return current.withFailure("io error: " + e.getMessage());
        } finally {
            deleteQuietly(tempAuthDir);
        }
    }

    private CommandResult clone(RepositoryConfig repo, Path workspace, Map<String, String> env, SecretEntry secret)
            throws IOException {
        Files.createDirectories(workspace.getParent());
        String remote = authenticatedRemote(repo.remote(), secret);
        List<String> argv = List.of(
                "git", "clone", "--branch", repo.branch(), "--single-branch", "--", remote, workspace.toString());
        return runner.run(argv, workspace.getParent(), env, GIT_TIMEOUT);
    }

    private CommandResult fetchAndReset(RepositoryConfig repo, Path workspace, Map<String, String> env) {
        CommandResult fetch = runner.run(
                List.of("git", "-C", workspace.toString(), "fetch", "--prune", "origin", repo.branch()),
                workspace,
                env,
                GIT_TIMEOUT);
        if (!fetch.succeeded()) {
            return fetch;
        }
        return runner.run(
                List.of("git", "-C", workspace.toString(), "reset", "--hard", "origin/" + repo.branch()),
                workspace,
                env,
                GIT_TIMEOUT);
    }

    private String headCommit(Path workspace) {
        CommandResult r = runner.run(
                List.of("git", "-C", workspace.toString(), "rev-parse", "HEAD"),
                workspace,
                Map.of(),
                Duration.ofSeconds(10));
        return r.succeeded() ? r.stdout().trim() : null;
    }

    // ---- authentication (no secrets in argv) ---------------------------------

    /**
     * Rewrites an HTTPS remote to embed a non-secret username for token auth, so git prompts only for
     * the password (supplied via askpass). Other cases are returned unchanged.
     */
    static String authenticatedRemote(String remote, SecretEntry secret) {
        if (secret != null
                && secret.authType() == io.nncdevel.codesearch.config.AuthType.TOKEN
                && remote.startsWith("https://")
                && !remote.substring("https://".length()).contains("@")) {
            return "https://x-access-token@" + remote.substring("https://".length());
        }
        return remote;
    }

    /**
     * Applies authentication to {@code env}. For token auth, writes a temporary askpass script (mode
     * 700) that echoes the token from an environment variable. Returns a temp directory to delete, or
     * {@code null}.
     */
    private Path applyAuth(Map<String, String> env, SecretEntry secret) throws IOException {
        if (secret == null) {
            return null;
        }
        switch (secret.authType()) {
            case TOKEN -> {
                Path dir = Files.createTempDirectory("codesearch-askpass");
                Path script = dir.resolve("askpass.sh");
                Files.writeString(script, "#!/bin/sh\nprintf '%s' \"$CODESEARCH_TOKEN\"\n");
                trySetExecutable(script);
                env.put("GIT_ASKPASS", script.toString());
                env.put("CODESEARCH_TOKEN", secret.token());
                return dir;
            }
            case SSH_KEY -> {
                env.put(
                        "GIT_SSH_COMMAND",
                        "ssh -i " + secret.sshKeyPath()
                                + " -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -o BatchMode=yes");
                return null;
            }
            case NONE -> {
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static void trySetExecutable(Path script) {
        try {
            Files.setPosixFilePermissions(
                    script,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException | IOException e) {
            // best effort on non-POSIX systems
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(GitSync::deleteOne);
        } catch (IOException e) {
            // best effort
        }
    }

    private static void deleteOne(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            // best effort
        }
    }

    private static String summarize(CommandResult result) {
        if (result.timedOut()) {
            return "git timed out";
        }
        String stderr = result.stderr().strip();
        String tail = stderr.length() > 300 ? stderr.substring(stderr.length() - 300) : stderr;
        return "git exited " + result.exitCode() + (tail.isEmpty() ? "" : ": " + tail);
    }
}
