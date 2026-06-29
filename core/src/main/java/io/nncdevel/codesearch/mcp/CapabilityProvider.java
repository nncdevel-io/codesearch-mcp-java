package io.nncdevel.codesearch.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.tool.ListFilesTool;
import io.nncdevel.codesearch.tool.ListRepositoriesTool;
import io.nncdevel.codesearch.tool.ListTreeTool;
import io.nncdevel.codesearch.tool.OutputMode;
import io.nncdevel.codesearch.tool.ReadFileTool;
import io.nncdevel.codesearch.tool.SearchCodeTool;
import io.nncdevel.codesearch.tool.SearchRequest;
import io.nncdevel.codesearch.tool.ToolContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Produces the MCP tool and resource specifications and the server capabilities from the core tools
 * (design §2.2, §3). The outer runtime (standalone or Spring) attaches these to an {@code McpServer}
 * of its chosen transport. This class never starts a server or selects a transport.
 */
public final class CapabilityProvider {

    private static final String MIME_JSON = "application/json";
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LIST_FILES_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration LIST_TREE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_FILE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CATALOG_TIMEOUT = Duration.ofSeconds(5);

    private final ToolContext context;
    private final ExecutionGuard guard;
    private final io.nncdevel.codesearch.obs.StructuredLogger logger;
    private final SearchCodeTool searchCode;
    private final ListFilesTool listFiles;
    private final ListTreeTool listTree;
    private final ReadFileTool readFile;
    private final ListRepositoriesTool listRepositories;

    /**
     * Constructs the provider.
     *
     * @param context the shared tool collaborators
     * @param guard the execution guard applied to every tool call
     * @param logger the structured logger used for DEBUG-level tool-call tracing
     */
    public CapabilityProvider(
            ToolContext context, ExecutionGuard guard, io.nncdevel.codesearch.obs.StructuredLogger logger) {
        this.context = context;
        this.guard = guard;
        this.logger = logger;
        this.searchCode = new SearchCodeTool(context);
        this.listFiles = new ListFilesTool(context);
        this.listTree = new ListTreeTool(context);
        this.readFile = new ReadFileTool(context);
        this.listRepositories = new ListRepositoriesTool(context);
    }

    /**
     * Returns the server capabilities: {@code tools} and {@code resources} only (no prompts).
     *
     * @return the advertised capabilities
     */
    public ServerCapabilities serverCapabilities() {
        return ServerCapabilities.builder().tools(true).resources(false, false).build();
    }

    /**
     * Builds the five tool specifications.
     *
     * @return the tool specifications in catalog order
     */
    public List<SyncToolSpecification> toolSpecifications() {
        List<SyncToolSpecification> specs = new ArrayList<>();
        specs.add(spec(
                "search_code",
                "Regex content search across a repository using ripgrep. Returns matching lines with"
                        + " 1-based line numbers and Git URLs. Supports glob/type filters, context lines,"
                        + " and output modes content|files_with_matches|count. Valid repository ids come"
                        + " from list_repositories or resources/list.",
                ToolSchemas.SEARCH_CODE,
                (exchange, request) -> guard.run(SEARCH_TIMEOUT, () -> {
                    Args a = new Args(request.arguments());
                    SearchRequest req = new SearchRequest(
                            a.str("pattern", null),
                            a.str("repository", null),
                            a.str("path", null),
                            a.str("glob", null),
                            a.str("type", null),
                            a.bool("case_sensitive", false),
                            OutputMode.fromWire(a.str("output_mode", null)),
                            a.intValue("context_before", 0),
                            a.intValue("context_after", 0),
                            a.intValue("max_results", 50));
                    return ok(searchCode.search(req));
                })));
        specs.add(spec(
                "list_files",
                "Glob-based filename search using ripgrep, newest first. Includes .gitignore'd and"
                        + " hidden files. Returns repository-relative paths with last_modified and Git URLs.",
                ToolSchemas.LIST_FILES,
                (exchange, request) -> guard.run(LIST_FILES_TIMEOUT, () -> {
                    Args a = new Args(request.arguments());
                    return ok(listFiles.list(
                            a.str("repository", null),
                            a.str("pattern", null),
                            a.str("path", null),
                            a.intValue("max_results", 100)));
                })));
        specs.add(spec(
                "list_tree",
                "Depth-limited directory overview built from git ls-files. Only Git-tracked files are"
                        + " shown. Returns an ASCII tree; use it to orient before searching.",
                ToolSchemas.LIST_TREE,
                (exchange, request) -> guard.run(LIST_TREE_TIMEOUT, () -> {
                    Args a = new Args(request.arguments());
                    return ok(listTree.list(
                            a.str("repository", null),
                            a.str("path", null),
                            a.intValue("max_depth", 2),
                            a.bool("show_files", true),
                            a.intValue("max_entries", 200)));
                })));
        specs.add(spec(
                "read_file",
                "Reads a line range from a text file with line-number prefixes and a Git URL. Rejects"
                        + " binary files and files over 10MB.",
                ToolSchemas.READ_FILE,
                (exchange, request) -> guard.run(READ_FILE_TIMEOUT, () -> {
                    Args a = new Args(request.arguments());
                    return ok(readFile.read(
                            a.str("repository", null),
                            a.str("file_path", null),
                            a.intValue("start_line", 1),
                            a.intValue("num_lines", 100)));
                })));
        specs.add(spec(
                "list_repositories",
                "Lists the configured repositories with their branch, hosting, and sync status. Use it"
                        + " to discover valid repository ids for the other tools.",
                ToolSchemas.LIST_REPOSITORIES,
                (exchange, request) -> guard.run(CATALOG_TIMEOUT, () -> ok(listRepositories.list()))));
        return specs;
    }

    /**
     * Builds one repository Resource specification per configured repository.
     *
     * @return the resource specifications in catalog order
     */
    public List<SyncResourceSpecification> resourceSpecifications() {
        List<SyncResourceSpecification> specs = new ArrayList<>();
        for (RepositoryConfig repo : context.repositories().all()) {
            String uri = "codesearch://repo/" + repo.id();
            String description =
                    repo.description() != null ? repo.description() : "Code search repository '" + repo.id() + "'";
            Resource resource = Resource.builder()
                    .uri(uri)
                    .name("Repository: " + repo.id())
                    .title(repo.id())
                    .description(description)
                    .mimeType(MIME_JSON)
                    .build();
            specs.add(new SyncResourceSpecification(resource, (exchange, request) -> {
                String json = ToolJson.toJson(listRepositories.entryFor(repo));
                return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), MIME_JSON, json)));
            }));
        }
        return specs;
    }

    private SyncToolSpecification spec(
            String name,
            String description,
            Map<String, Object> inputSchema,
            java.util.function.BiFunction<
                            io.modelcontextprotocol.server.McpSyncServerExchange,
                            io.modelcontextprotocol.spec.McpSchema.CallToolRequest,
                            CallToolResult>
                    handler) {
        Tool tool = Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(traced(name, handler))
                .build();
    }

    /**
     * Wraps a tool handler with DEBUG-level request/result tracing. This is the project's
     * JSON-RPC-level debug logging at the tool-call boundary (requirements §5.6). Full wire-level
     * message tee is not exposed by the MCP SDK transport layer; see docs/devel/operation.
     */
    private java.util.function.BiFunction<
                    io.modelcontextprotocol.server.McpSyncServerExchange,
                    io.modelcontextprotocol.spec.McpSchema.CallToolRequest,
                    CallToolResult>
            traced(
                    String name,
                    java.util.function.BiFunction<
                                    io.modelcontextprotocol.server.McpSyncServerExchange,
                                    io.modelcontextprotocol.spec.McpSchema.CallToolRequest,
                                    CallToolResult>
                            handler) {
        return (exchange, request) -> {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "jsonrpc_tool_call",
                        Map.of(
                                "tool",
                                name,
                                "arguments",
                                request.arguments() == null ? Map.of() : request.arguments()));
            }
            CallToolResult result = handler.apply(exchange, request);
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "jsonrpc_tool_result", Map.of("tool", name, "is_error", Boolean.TRUE.equals(result.isError())));
            }
            return result;
        };
    }

    private static CallToolResult ok(Object result) {
        return CallToolResult.builder()
                .structuredContent(ToolJson.toStructured(result))
                .addTextContent(ToolJson.toJson(result))
                .build();
    }

    /** Minimal typed accessors over the MCP argument map. */
    private static final class Args {
        private final Map<String, Object> map;

        private Args(Map<String, Object> map) {
            this.map = map == null ? Map.of() : map;
        }

        private String str(String key, String fallback) {
            Object v = map.get(key);
            return v == null ? fallback : v.toString();
        }

        private boolean bool(String key, boolean fallback) {
            Object v = map.get(key);
            return v instanceof Boolean b ? b : fallback;
        }

        private int intValue(String key, int fallback) {
            Object v = map.get(key);
            return v instanceof Number n ? n.intValue() : fallback;
        }
    }
}
