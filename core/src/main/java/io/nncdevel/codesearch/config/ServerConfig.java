package io.nncdevel.codesearch.config;

/**
 * Resolved {@code serve} startup parameters (spec §7.3).
 *
 * @param transport the MCP transport to expose
 * @param host the HTTP bind address (used only for {@link Transport#HTTP})
 * @param port the HTTP bind port (used only for {@link Transport#HTTP})
 * @param enableScheduler whether the in-process periodic sync scheduler is enabled
 */
public record ServerConfig(Transport transport, String host, int port, boolean enableScheduler) {

    /** Default transport when none is configured. */
    public static final Transport DEFAULT_TRANSPORT = Transport.STDIO;

    /** Default HTTP bind address when none is configured. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Default HTTP bind port when none is configured. */
    public static final int DEFAULT_PORT = 8000;

    /** Default scheduler enablement when none is configured. */
    public static final boolean DEFAULT_ENABLE_SCHEDULER = false;

    /**
     * Returns the built-in default configuration.
     *
     * @return a configuration using all default values
     */
    public static ServerConfig defaults() {
        return new ServerConfig(DEFAULT_TRANSPORT, DEFAULT_HOST, DEFAULT_PORT, DEFAULT_ENABLE_SCHEDULER);
    }
}
