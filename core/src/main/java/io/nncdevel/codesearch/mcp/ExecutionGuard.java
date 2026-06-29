package io.nncdevel.codesearch.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ErrorJson;
import io.nncdevel.codesearch.error.ToolException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Guards tool execution with a concurrency limit and timeouts (spec §8). At most {@value
 * #MAX_CONCURRENT} tool calls run at once; calls that cannot start within the overall budget, or
 * that exceed their per-tool timeout, return a {@code TIMEOUT} error result. Domain {@link
 * ToolException}s are mapped to {@code isError} results; any other failure becomes {@code
 * INTERNAL_ERROR}.
 */
public final class ExecutionGuard implements AutoCloseable {

    /** Maximum number of concurrent tool calls (spec §8.2). */
    public static final int MAX_CONCURRENT = 16;

    /** Overall budget including queue wait (spec §8.2). */
    private static final Duration OVERALL_BUDGET = Duration.ofSeconds(30);

    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** Creates a guard with a virtual-thread executor and a 16-permit semaphore. */
    public ExecutionGuard() {}

    /**
     * Runs a tool body under the concurrency and timeout limits, converting failures to error
     * results.
     *
     * @param toolTimeout the per-tool timeout
     * @param body the tool body, which produces a success result or throws {@link ToolException}
     * @return the tool result, or an error result on timeout or failure
     */
    public CallToolResult run(Duration toolTimeout, Callable<CallToolResult> body) {
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(OVERALL_BUDGET.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(new ToolException(ErrorCode.INTERNAL_ERROR, "Interrupted while queued"));
        }
        if (!acquired) {
            return error(new ToolException(ErrorCode.TIMEOUT, "Server is busy; request timed out"));
        }
        Future<CallToolResult> future = executor.submit(body);
        try {
            return future.get(toolTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return error(new ToolException(ErrorCode.TIMEOUT, "Tool execution timed out"));
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return error(new ToolException(ErrorCode.INTERNAL_ERROR, "Interrupted during execution"));
        } catch (ExecutionException e) {
            return error(mapCause(e.getCause()));
        } finally {
            semaphore.release();
        }
    }

    private static ToolException mapCause(Throwable cause) {
        if (cause instanceof ToolException te) {
            return te;
        }
        String message = cause == null
                ? "Internal error"
                : "Internal error: " + cause.getClass().getSimpleName();
        return new ToolException(ErrorCode.INTERNAL_ERROR, message);
    }

    /**
     * Builds an {@code isError} tool result carrying the {@code {code,message,details}} JSON.
     *
     * @param error the domain error
     * @return the error result
     */
    public static CallToolResult error(ToolException error) {
        return CallToolResult.builder()
                .isError(true)
                .addTextContent(ErrorJson.serialize(error))
                .structuredContent(Map.of("code", error.code().code(), "message", error.getMessage()))
                .build();
    }

    @Override
    public void close() {
        executor.close();
    }
}
