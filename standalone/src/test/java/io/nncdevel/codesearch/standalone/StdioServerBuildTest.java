package io.nncdevel.codesearch.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.modelcontextprotocol.server.McpSyncServer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the stdio server is assembled and bound (construction-level). End-to-end MCP behavior
 * over the shared capability stack is covered by {@link HttpEndToEndTest}; full stdio behavior is
 * additionally verifiable manually via {@code serve --transport stdio} with an MCP host.
 */
class StdioServerBuildTest {

    @Test
    void buildsStdioServerWithServerInfo(@TempDir Path base) throws Exception {
        Path ws = base.resolve("ws");
        Files.createDirectories(ws);
        Path repos = base.resolve("repos.json");
        Files.writeString(repos, """
        {
          "workspace_root": "%s",
          "repositories": [
            {"id": "r1", "remote": "x", "branch": "main", "hosting": "github",
             "hosting_base_url": "https://github.com/x/r1"}
          ]
        }
        """.formatted(ws.toString().replace("\\", "\\\\")));

        try (ServerComponents components = ServerComponents.load(repos, null, ws)) {
            McpSyncServer server = components.buildStdioServer();
            assertNotNull(server);
            assertEquals("codesearch-mcp", server.getServerInfo().name());
            assertEquals(ServerComponents.VERSION, server.getServerInfo().version());
            server.close();
        }
    }
}
