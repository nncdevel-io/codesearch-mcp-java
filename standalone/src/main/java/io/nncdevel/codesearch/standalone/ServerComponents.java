package io.nncdevel.codesearch.standalone;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.nncdevel.codesearch.mcp.CapabilityProvider;
import io.nncdevel.codesearch.mcp.ServerAssembly;
import io.nncdevel.codesearch.obs.StructuredLogger;
import io.nncdevel.codesearch.repo.GitSync;
import io.nncdevel.codesearch.repo.RepositoryManager;
import java.nio.file.Path;

/**
 * Standalone composition root: wraps the framework-agnostic {@link ServerAssembly} and builds an
 * {@link McpSyncServer} for stdio or Streamable HTTP (design §4.1).
 */
public final class ServerComponents implements AutoCloseable {

    /** Advertised server version. */
    public static final String VERSION = "0.1.0";

    private static final String SERVER_NAME = "codesearch-mcp";
    private static final McpJsonMapper JSON = new JacksonMcpJsonMapperSupplier().get();

    private final ServerAssembly assembly;

    private ServerComponents(ServerAssembly assembly) {
        this.assembly = assembly;
    }

    /**
     * Loads configuration and assembles the runtime components.
     *
     * @param reposPath the resolved repos.json path
     * @param secretsPath the resolved secrets.json path, or {@code null} for anonymous access
     * @param workspaceRoot the resolved workspace root
     * @return the assembled components
     */
    public static ServerComponents load(Path reposPath, Path secretsPath, Path workspaceRoot) {
        return new ServerComponents(ServerAssembly.load(reposPath, secretsPath, workspaceRoot));
    }

    /** Returns the shared default JSON mapper for transport providers. */
    static McpJsonMapper jsonMapper() {
        return JSON;
    }

    /**
     * Returns the repository manager.
     *
     * @return the repository manager
     */
    public RepositoryManager repositoryManager() {
        return assembly.repositoryManager();
    }

    /**
     * Returns the git sync engine.
     *
     * @return the git sync engine
     */
    public GitSync gitSync() {
        return assembly.gitSync();
    }

    /**
     * Returns the structured logger.
     *
     * @return the structured logger
     */
    public StructuredLogger logger() {
        return assembly.logger();
    }

    /**
     * Builds a sync server over the stdio transport.
     *
     * @return the configured sync server
     */
    public McpSyncServer buildStdioServer() {
        return assemble(McpServer.sync(new StdioServerTransportProvider(JSON)));
    }

    /**
     * Builds a sync server over the given Streamable HTTP servlet transport provider.
     *
     * @param provider the servlet transport provider to bind
     * @return the configured sync server
     */
    public McpSyncServer buildHttpServer(HttpServletStreamableServerTransportProvider provider) {
        return assemble(McpServer.sync(provider));
    }

    private McpSyncServer assemble(McpServer.SyncSpecification<?> spec) {
        CapabilityProvider capabilities = assembly.capabilities();
        return spec.serverInfo(SERVER_NAME, VERSION)
                .capabilities(capabilities.serverCapabilities())
                .tools(capabilities.toolSpecifications())
                .resources(capabilities.resourceSpecifications())
                .build();
    }

    @Override
    public void close() {
        assembly.close();
    }
}
