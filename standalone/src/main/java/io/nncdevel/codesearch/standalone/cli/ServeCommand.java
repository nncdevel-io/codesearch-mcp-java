package io.nncdevel.codesearch.standalone.cli;

import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.config.ConfigException;
import io.nncdevel.codesearch.config.RepositoriesFile;
import io.nncdevel.codesearch.config.ServerConfig;
import io.nncdevel.codesearch.config.Transport;
import io.nncdevel.codesearch.repo.SyncScheduler;
import io.nncdevel.codesearch.standalone.HttpTransport;
import io.nncdevel.codesearch.standalone.ServeNotifier;
import io.nncdevel.codesearch.standalone.ServerComponents;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Runs the MCP server over stdio or Streamable HTTP. */
@Command(name = "serve", description = "Run the MCP server (stdio or Streamable HTTP).")
final class ServeCommand implements Callable<Integer> {

    @Option(names = "--transport", description = "stdio | http")
    private String transport;

    @Option(names = "--host", description = "HTTP bind address")
    private String host;

    @Option(names = "--port", description = "HTTP bind port")
    private Integer port;

    @Option(names = "--repos", description = "Path to repos.json")
    private String repos;

    @Option(names = "--secrets", description = "Path to secrets.json")
    private String secrets;

    @Option(names = "--config", description = "Path to server.json")
    private String config;

    @Option(names = "--workspace-root", description = "Clone root directory")
    private String workspaceRoot;

    @Option(names = "--enable-scheduler", negatable = true, description = "In-process periodic sync")
    private Boolean enableScheduler;

    /** Required by picocli. */
    ServeCommand() {}

    @Override
    public Integer call() throws Exception {
        Path reposPath = Cli.resolveReposPath(repos);
        if (reposPath == null) {
            System.err.println("repos.json is required (use --repos, CODE_SEARCH_REPOS_PATH, or ./config/repos.json)");
            return 2;
        }
        ServerComponents components;
        Path workspacePath;
        Transport effectiveTransport;
        String effectiveHost;
        int effectivePort;
        boolean scheduler;
        try {
            RepositoriesFile reposFile = io.nncdevel.codesearch.config.ConfigLoader.loadRepositories(reposPath);
            Path secretsPath = Cli.resolveSecretsPath(secrets, reposFile);
            workspacePath = Cli.resolveWorkspaceRoot(workspaceRoot, reposFile);
            ServerConfig fileConfig =
                    io.nncdevel.codesearch.config.ConfigLoader.loadServerConfig(Cli.resolveServerConfigPath(config));
            effectiveTransport = transport != null ? Transport.fromWire(transport) : fileConfig.transport();
            effectiveHost = host != null ? host : fileConfig.host();
            effectivePort = port != null ? port : fileConfig.port();
            scheduler = enableScheduler != null ? enableScheduler : fileConfig.enableScheduler();
            components = ServerComponents.load(reposPath, secretsPath, workspacePath);
        } catch (ConfigException e) {
            System.err.println("configuration error: " + e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println("invalid option: " + e.getMessage());
            return 2;
        }

        Path pidFile = ServeNotifier.pidFile(workspacePath);
        ServeNotifier.writeServePid(pidFile);
        ServeNotifier.registerReloadHandler(
                () -> components.repositoryManager().refreshStatesFromDisk(new CommandRunner()));

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ServeNotifier.deletePid(pidFile);
            components.close();
            shutdown.countDown();
        }));

        SyncScheduler syncScheduler = null;
        if (scheduler) {
            syncScheduler =
                    new SyncScheduler(components.gitSync(), components.repositoryManager(), components.logger());
            syncScheduler.start();
        }

        components.logger().info("server_starting", Map.of("transport", effectiveTransport.wire()));
        try {
            if (effectiveTransport == Transport.STDIO) {
                components.buildStdioServer();
                shutdown.await();
            } else {
                try (HttpTransport http = new HttpTransport(components, effectiveHost, effectivePort)) {
                    http.start();
                    components.logger().info("http_listening", Map.of("host", effectiveHost, "port", http.port()));
                    http.join();
                }
            }
        } finally {
            if (syncScheduler != null) {
                syncScheduler.close();
            }
        }
        return 0;
    }
}
