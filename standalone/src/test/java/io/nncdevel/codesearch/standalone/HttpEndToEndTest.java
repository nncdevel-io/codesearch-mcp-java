package io.nncdevel.codesearch.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("requires_git")
@Tag("requires_rg")
class HttpEndToEndTest {

    private Path writeConfig(Path base) throws Exception {
        Path ws = base.resolve("ws");
        Path repoDir = ws.resolve("r1");
        Files.createDirectories(repoDir);
        git(repoDir, "init", "-b", "main");
        git(repoDir, "config", "user.email", "t@example.com");
        git(repoDir, "config", "user.name", "T");
        Files.createDirectories(repoDir.resolve("src"));
        Files.writeString(repoDir.resolve("src/A.java"), "class A {}\n// TODO here\n");
        git(repoDir, "add", "-A");
        git(repoDir, "commit", "-m", "init");

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
        return repos;
    }

    private static void git(Path dir, String... args) throws Exception {
        java.util.List<String> argv = new java.util.ArrayList<>();
        argv.add("git");
        argv.addAll(List.of(args));
        Process p = new ProcessBuilder(argv)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        p.getInputStream().readAllBytes();
        p.waitFor();
    }

    @Test
    void httpClientListsToolsAndSearches(@TempDir Path base) throws Exception {
        Path repos = writeConfig(base);
        Path ws = base.resolve("ws");
        try (ServerComponents components = ServerComponents.load(repos, null, ws);
                HttpTransport http = new HttpTransport(components, "127.0.0.1", 0)) {
            http.start();
            int port = http.port();

            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                            "http://127.0.0.1:" + port)
                    .build();
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(20))
                    .build();
            try {
                client.initialize();

                ListToolsResult tools = client.listTools();
                List<String> names = tools.tools().stream().map(t -> t.name()).toList();
                assertTrue(names.contains("search_code"), names.toString());
                assertEquals(5, names.size());

                CallToolResult result = client.callTool(
                        new CallToolRequest("search_code", Map.of("pattern", "TODO", "repository", "r1")));
                assertFalse(Boolean.TRUE.equals(result.isError()));
                assertTrue(result.structuredContent() instanceof Map<?, ?>);
                Map<?, ?> structured = (Map<?, ?>) result.structuredContent();
                assertEquals(1, ((Number) structured.get("total_matches")).intValue());
            } finally {
                client.closeGracefully();
            }
        }
    }

    @Test
    void unknownRepositoryReturnsIsError(@TempDir Path base) throws Exception {
        Path repos = writeConfig(base);
        Path ws = base.resolve("ws");
        try (ServerComponents components = ServerComponents.load(repos, null, ws);
                HttpTransport http = new HttpTransport(components, "127.0.0.1", 0)) {
            http.start();
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(
                            "http://127.0.0.1:" + http.port())
                    .build();
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(20))
                    .build();
            try {
                client.initialize();
                CallToolResult result =
                        client.callTool(new CallToolRequest("list_tree", Map.of("repository", "missing")));
                assertTrue(Boolean.TRUE.equals(result.isError()));
            } finally {
                client.closeGracefully();
            }
        }
    }
}
