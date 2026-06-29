package io.nncdevel.codesearch.backend;

import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs external commands without a shell, using {@link ProcessBuilder} and virtual threads to drain
 * stdout/stderr concurrently (design §1, §6). The shell is never invoked, so arguments are not
 * subject to shell injection.
 */
public final class CommandRunner {

    /** Creates a command runner. */
    public CommandRunner() {}

    /**
     * Runs {@code argv} in {@code workdir} with optional extra environment, killing the process if it
     * exceeds {@code timeout}.
     *
     * @param argv the command and its arguments; the first element is the executable
     * @param workdir the working directory, or {@code null} to inherit the current one
     * @param env extra environment variables to add, or {@code null}/empty for none
     * @param timeout the maximum run time, or {@code null} to wait indefinitely
     * @return the captured exit code, stdout, stderr, and timeout flag
     * @throws ToolException with {@link ErrorCode#BACKEND_FAILURE} if the process cannot be started or
     *     the calling thread is interrupted
     */
    public CommandResult run(List<String> argv, Path workdir, Map<String, String> env, Duration timeout) {
        ProcessBuilder pb = new ProcessBuilder(argv);
        if (workdir != null) {
            pb.directory(workdir.toFile());
        }
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ToolException(
                    ErrorCode.BACKEND_FAILURE,
                    "Failed to start backend command",
                    Map.of("command", argv.isEmpty() ? "" : argv.get(0)));
        }

        Future<String> out = readStreamAsync(process.getInputStream());
        Future<String> err = readStreamAsync(process.getErrorStream());
        try {
            boolean finished =
                    timeout == null ? waitForever(process) : process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return new CommandResult(-1, drain(out), drain(err), true);
            }
            return new CommandResult(process.exitValue(), drain(out), drain(err), false);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ToolException(ErrorCode.BACKEND_FAILURE, "Backend command was interrupted");
        }
    }

    private static boolean waitForever(Process process) throws InterruptedException {
        process.waitFor();
        return true;
    }

    private static Future<String> readStreamAsync(InputStream stream) {
        java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (stream) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                stream.transferTo(buffer);
                future.complete(buffer.toString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                future.complete("");
            }
        });
        return future;
    }

    private static String drain(Future<String> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            return "";
        }
    }
}
