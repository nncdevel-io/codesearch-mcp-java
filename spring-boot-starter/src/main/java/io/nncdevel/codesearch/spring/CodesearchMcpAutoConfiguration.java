package io.nncdevel.codesearch.spring;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.nncdevel.codesearch.mcp.CapabilityProvider;
import io.nncdevel.codesearch.mcp.ServerAssembly;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the Code Search MCP server on the host application's servlet container (ADR-0001). The
 * core {@link CapabilityProvider} is attached to a Streamable HTTP servlet transport, which is
 * exposed at {@code codesearch.mcp.endpoint}. Active only in a servlet web application with
 * {@code codesearch.mcp.repos-path} set.
 */
@AutoConfiguration
@ConditionalOnClass(McpServer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "codesearch.mcp", name = "repos-path")
@EnableConfigurationProperties(CodesearchMcpProperties.class)
public class CodesearchMcpAutoConfiguration {

    private static final String SERVER_NAME = "codesearch-mcp";
    private static final String VERSION = "0.1.0";
    private static final String DEFAULT_WORKSPACE = "workspaces";

    /** Required by Spring. */
    public CodesearchMcpAutoConfiguration() {}

    /**
     * Loads configuration and assembles the core components.
     *
     * @param properties the bound configuration properties
     * @return the server assembly (closed on context shutdown)
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ServerAssembly codesearchServerAssembly(CodesearchMcpProperties properties) {
        Path reposPath = Path.of(properties.reposPath());
        Path secretsPath = properties.secretsPath() == null ? null : Path.of(properties.secretsPath());
        Path workspaceRoot =
                properties.workspaceRoot() == null ? Path.of(DEFAULT_WORKSPACE) : Path.of(properties.workspaceRoot());
        return ServerAssembly.load(reposPath, secretsPath, workspaceRoot);
    }

    /**
     * Creates the Streamable HTTP servlet transport provider.
     *
     * @param properties the bound configuration properties
     * @return the servlet transport provider
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpServletStreamableServerTransportProvider codesearchMcpTransportProvider(
            CodesearchMcpProperties properties) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapperSupplier().get())
                .mcpEndpoint(properties.endpoint())
                .build();
    }

    /**
     * Builds and binds the MCP sync server to the transport provider.
     *
     * @param assembly the core assembly
     * @param provider the servlet transport provider
     * @return the running sync server (closed on context shutdown)
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public McpSyncServer codesearchMcpServer(
            ServerAssembly assembly, HttpServletStreamableServerTransportProvider provider) {
        CapabilityProvider capabilities = assembly.capabilities();
        return McpServer.sync(provider)
                .serverInfo(SERVER_NAME, VERSION)
                .capabilities(capabilities.serverCapabilities())
                .tools(capabilities.toolSpecifications())
                .resources(capabilities.resourceSpecifications())
                .build();
    }

    /**
     * Mounts the MCP servlet at the configured endpoint. Depends on the sync server so the transport
     * is bound before requests arrive.
     *
     * @param provider the servlet transport provider
     * @param properties the bound configuration properties
     * @param mcpServer the built sync server (ensures binding order)
     * @return the servlet registration
     */
    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> codesearchMcpServlet(
            HttpServletStreamableServerTransportProvider provider,
            CodesearchMcpProperties properties,
            McpSyncServer mcpServer) {
        return new ServletRegistrationBean<>(provider, properties.endpoint() + "/*");
    }
}
