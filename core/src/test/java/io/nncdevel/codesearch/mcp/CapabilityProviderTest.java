package io.nncdevel.codesearch.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.backend.GitLsBackend;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.testsupport.GitFixture;
import io.nncdevel.codesearch.tool.ToolContext;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("requires_git")
@Tag("requires_rg")
class CapabilityProviderTest {

    private CapabilityProvider provider(Path base, ExecutionGuard guard) throws Exception {
        Path ws = base.resolve("ws");
        Path repoDir = ws.resolve("r1");
        Files.createDirectories(repoDir);
        GitFixture.git(repoDir, "init", "-b", "main");
        GitFixture.git(repoDir, "config", "user.email", "t@example.com");
        GitFixture.git(repoDir, "config", "user.name", "T");
        Files.createDirectories(repoDir.resolve("src"));
        Files.writeString(repoDir.resolve("src/A.java"), "class A {}\n// TODO x\n");
        GitFixture.git(repoDir, "add", "-A");
        GitFixture.git(repoDir, "commit", "-m", "init");
        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, List.of(), "demo repo");
        RepositoryManager m = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        m.refreshStatesFromDisk(new CommandRunner());
        CommandRunner runner = new CommandRunner();
        ToolContext ctx = new ToolContext(m, new RipgrepBackend(runner), new GitLsBackend(runner));
        return new CapabilityProvider(ctx, guard, io.nncdevel.codesearch.obs.StructuredLogger.forCategory("test.mcp"));
    }

    private SyncToolSpecification toolNamed(CapabilityProvider p, String name) {
        return p.toolSpecifications().stream()
                .filter(s -> s.tool().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void advertisesFiveToolsAndCapabilities(@TempDir Path base) throws Exception {
        try (ExecutionGuard guard = new ExecutionGuard()) {
            CapabilityProvider p = provider(base, guard);
            List<String> names =
                    p.toolSpecifications().stream().map(s -> s.tool().name()).toList();
            assertEquals(List.of("search_code", "list_files", "list_tree", "read_file", "list_repositories"), names);
            assertTrue(p.serverCapabilities().tools() != null);
            assertTrue(p.serverCapabilities().resources() != null);
            assertTrue(p.serverCapabilities().prompts() == null, "prompts not advertised");
        }
    }

    @Test
    void searchCodeHandlerReturnsStructuredContent(@TempDir Path base) throws Exception {
        try (ExecutionGuard guard = new ExecutionGuard()) {
            CapabilityProvider p = provider(base, guard);
            CallToolResult result = toolNamed(p, "search_code")
                    .callHandler()
                    .apply(null, new CallToolRequest("search_code", Map.of("pattern", "TODO", "repository", "r1")));
            assertFalse(Boolean.TRUE.equals(result.isError()));
            assertTrue(result.structuredContent() instanceof Map<?, ?>);
            Map<?, ?> structured = (Map<?, ?>) result.structuredContent();
            assertTrue(structured.containsKey("matches"));
            assertTrue(structured.containsKey("total_matches"));
        }
    }

    @Test
    void domainErrorBecomesIsErrorResult(@TempDir Path base) throws Exception {
        try (ExecutionGuard guard = new ExecutionGuard()) {
            CapabilityProvider p = provider(base, guard);
            CallToolResult result = toolNamed(p, "read_file")
                    .callHandler()
                    .apply(
                            null,
                            new CallToolRequest("read_file", Map.of("repository", "r1", "file_path", "../escape")));
            assertTrue(Boolean.TRUE.equals(result.isError()));
            String text = ((TextContent) result.content().get(0)).text();
            assertTrue(text.contains("INVALID_PATH"), text);
        }
    }

    @Test
    void unknownRepositoryBecomesRepoNotFound(@TempDir Path base) throws Exception {
        try (ExecutionGuard guard = new ExecutionGuard()) {
            CapabilityProvider p = provider(base, guard);
            CallToolResult result = toolNamed(p, "list_tree")
                    .callHandler()
                    .apply(null, new CallToolRequest("list_tree", Map.of("repository", "missing")));
            assertTrue(Boolean.TRUE.equals(result.isError()));
            assertTrue(((TextContent) result.content().get(0)).text().contains("REPO_NOT_FOUND"));
        }
    }

    @Test
    void repositoryResourceReadsCatalogJson(@TempDir Path base) throws Exception {
        try (ExecutionGuard guard = new ExecutionGuard()) {
            CapabilityProvider p = provider(base, guard);
            List<SyncResourceSpecification> resources = p.resourceSpecifications();
            assertEquals(1, resources.size());
            assertEquals("codesearch://repo/r1", resources.get(0).resource().uri());
            ReadResourceResult read =
                    resources.get(0).readHandler().apply(null, new ReadResourceRequest("codesearch://repo/r1"));
            TextResourceContents contents =
                    (TextResourceContents) read.contents().get(0);
            assertTrue(contents.text().contains("\"id\":\"r1\""));
            assertTrue(contents.text().contains("\"hosting\":\"github\""));
        }
    }
}
