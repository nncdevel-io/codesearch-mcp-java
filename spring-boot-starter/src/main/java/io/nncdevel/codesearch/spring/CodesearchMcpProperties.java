package io.nncdevel.codesearch.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Code Search MCP server, bound from {@code codesearch.mcp.*}.
 *
 * @param reposPath path to repos.json (required)
 * @param secretsPath path to secrets.json, or {@code null} for anonymous access
 * @param workspaceRoot clone root directory; defaults to {@code ./workspaces} when {@code null}
 * @param endpoint the servlet path the MCP transport is mounted at
 */
@ConfigurationProperties(prefix = "codesearch.mcp")
public record CodesearchMcpProperties(String reposPath, String secretsPath, String workspaceRoot, String endpoint) {

    /** Default MCP endpoint path. */
    public static final String DEFAULT_ENDPOINT = "/mcp";

    /** Applies the default endpoint when unset. */
    public CodesearchMcpProperties {
        endpoint = (endpoint == null || endpoint.isBlank()) ? DEFAULT_ENDPOINT : endpoint;
    }
}
