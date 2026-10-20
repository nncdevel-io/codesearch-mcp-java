package io.nncdevel.codesearch.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.server.McpSyncServer;
import io.nncdevel.codesearch.mcp.ServerAssembly;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class CodesearchMcpAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CodesearchMcpAutoConfiguration.class));

    private Path writeRepos(Path dir) throws IOException {
        Path ws = dir.resolve("ws");
        Files.createDirectories(ws);
        Path repos = dir.resolve("repos.json");
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

    @Test
    void registersBeansWhenReposPathSet(@TempDir Path dir) throws IOException {
        Path repos = writeRepos(dir);
        runner.withPropertyValues("codesearch.mcp.repos-path=" + repos).run(context -> {
            assertThat(context).hasSingleBean(ServerAssembly.class);
            assertThat(context).hasSingleBean(McpSyncServer.class);
            assertThat(context).hasBean("codesearchMcpServlet");
        });
    }

    @Test
    void inactiveWithoutReposPath() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ServerAssembly.class));
    }

    @Test
    void usesConfiguredEndpoint(@TempDir Path dir) throws IOException {
        Path repos = writeRepos(dir);
        runner.withPropertyValues("codesearch.mcp.repos-path=" + repos, "codesearch.mcp.endpoint=/api/mcp")
                .run(context -> {
                    CodesearchMcpProperties props = context.getBean(CodesearchMcpProperties.class);
                    assertThat(props.endpoint()).isEqualTo("/api/mcp");
                });
    }
}
