package io.nncdevel.codesearch.standalone.cli;

import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.config.ConfigException;
import io.nncdevel.codesearch.config.ConfigLoader;
import io.nncdevel.codesearch.config.RepositoriesFile;
import io.nncdevel.codesearch.obs.Observability;
import io.nncdevel.codesearch.standalone.ServeNotifier;
import io.nncdevel.codesearch.standalone.ServerComponents;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Synchronizes all repositories once, then signals a running {@code serve} to reload. */
@Command(name = "sync", description = "Sync all repositories once and notify a running server.")
final class SyncCommand implements Callable<Integer> {

    @Option(names = "--repos", description = "Path to repos.json")
    private String repos;

    @Option(names = "--secrets", description = "Path to secrets.json")
    private String secrets;

    @Option(names = "--workspace-root", description = "Clone root directory")
    private String workspaceRoot;

    /** Required by picocli. */
    SyncCommand() {}

    @Override
    public Integer call() {
        Path reposPath = Cli.resolveReposPath(repos);
        if (reposPath == null) {
            System.err.println("repos.json is required (use --repos, CODE_SEARCH_REPOS_PATH, or ./config/repos.json)");
            return 2;
        }
        try {
            RepositoriesFile reposFile = ConfigLoader.loadRepositories(reposPath);
            Path secretsPath = Cli.resolveSecretsPath(secrets, reposFile);
            Path workspacePath = Cli.resolveWorkspaceRoot(workspaceRoot, reposFile);
            try (ServerComponents components = ServerComponents.load(reposPath, secretsPath, workspacePath)) {
                components.gitSync().syncAll(components.repositoryManager());
                Observability.logStatus(components.logger(), components.repositoryManager());
                boolean notified = ServeNotifier.notifyServe(ServeNotifier.pidFile(workspacePath), new CommandRunner());
                components.logger().info("sync_complete", Map.of("notified_serve", notified));
            }
            return 0;
        } catch (ConfigException e) {
            System.err.println("configuration error: " + e.getMessage());
            return 1;
        }
    }
}
