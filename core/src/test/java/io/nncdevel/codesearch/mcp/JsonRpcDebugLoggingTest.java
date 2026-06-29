package io.nncdevel.codesearch.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.nncdevel.codesearch.backend.CommandRunner;
import io.nncdevel.codesearch.backend.GitLsBackend;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.config.SecretsFile;
import io.nncdevel.codesearch.obs.StructuredLogger;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.tool.ToolContext;
import io.nncdevel.codesearch.util.Hosting;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/** Verifies that JSON-RPC tool-call tracing is emitted at DEBUG and suppressed at INFO (spec §5.6). */
class JsonRpcDebugLoggingTest {

    private static final String CATEGORY = "test.debug.jsonrpc";

    private CapabilityProvider provider(Path ws) {
        RepositoryConfig repo = new RepositoryConfig(
                "r1", "x", "main", Hosting.GITHUB, "https://github.com/x/r1", 900, List.of(), null);
        RepositoryManager manager = new RepositoryManager(ws, List.of(repo), SecretsFile.empty());
        CommandRunner runner = new CommandRunner();
        ToolContext ctx = new ToolContext(manager, new RipgrepBackend(runner), new GitLsBackend(runner));
        return new CapabilityProvider(ctx, new ExecutionGuard(), StructuredLogger.forCategory(CATEGORY));
    }

    private long invokeListRepositories(CapabilityProvider provider) {
        return provider.toolSpecifications().stream()
                .filter(s -> s.tool().name().equals("list_repositories"))
                .findFirst()
                .orElseThrow()
                .callHandler()
                .apply(null, new CallToolRequest("list_repositories", Map.of()))
                .content()
                .size();
    }

    @Test
    void emittedAtDebugSuppressedAtInfo(@TempDir Path ws) {
        Logger logback = (Logger) LoggerFactory.getLogger(CATEGORY);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logback.addAppender(appender);
        try {
            CapabilityProvider provider = provider(ws);

            logback.setLevel(Level.DEBUG);
            invokeListRepositories(provider);
            assertTrue(
                    appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("jsonrpc_tool_call")),
                    "tool_call event should be logged at DEBUG");
            assertTrue(
                    appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("jsonrpc_tool_result")),
                    "tool_result event should be logged at DEBUG");

            appender.list.clear();
            logback.setLevel(Level.INFO);
            invokeListRepositories(provider);
            assertFalse(
                    appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("jsonrpc_tool")),
                    "no JSON-RPC tracing at INFO");
        } finally {
            logback.detachAppender(appender);
            logback.setLevel(null);
        }
    }
}
