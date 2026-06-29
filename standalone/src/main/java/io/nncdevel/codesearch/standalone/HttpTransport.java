package io.nncdevel.codesearch.standalone;

import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/**
 * Embeds Jetty to serve the Streamable HTTP transport on {@code /mcp} (design §4.1). The MCP server
 * is built and bound to the servlet transport provider before Jetty starts.
 */
public final class HttpTransport implements AutoCloseable {

    private final Server server;
    private final ServerConnector connector;

    /**
     * Builds the embedded HTTP server and binds the MCP servlet transport at {@code /mcp}.
     *
     * @param components the assembled core components
     * @param host the bind address
     * @param port the bind port (0 selects an ephemeral port)
     */
    public HttpTransport(ServerComponents components, String host, int port) {
        HttpServletStreamableServerTransportProvider provider = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(ServerComponents.jsonMapper())
                .mcpEndpoint("/mcp")
                .build();
        components.buildHttpServer(provider);

        this.server = new Server();
        this.connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(provider), "/mcp/*");
        server.setHandler(context);
    }

    /**
     * Starts the HTTP server.
     *
     * @throws Exception if Jetty fails to start
     */
    public void start() throws Exception {
        server.start();
    }

    /**
     * Returns the actual bound port (useful when an ephemeral port was requested).
     *
     * @return the local port, or {@code -1} if not bound
     */
    public int port() {
        return connector.getLocalPort();
    }

    /**
     * Blocks until the server stops.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void join() throws InterruptedException {
        server.join();
    }

    /** Stops the server, ignoring shutdown errors. */
    @Override
    public void close() {
        try {
            server.stop();
        } catch (Exception e) {
            // best effort on shutdown
        }
    }
}
