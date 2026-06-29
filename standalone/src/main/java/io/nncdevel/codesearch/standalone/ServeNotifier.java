package io.nncdevel.codesearch.standalone;

import io.nncdevel.codesearch.backend.CommandResult;
import io.nncdevel.codesearch.backend.CommandRunner;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sync-to-serve notification via a PID file and {@code SIGHUP} (ADR-0004). The {@code serve} process
 * writes its PID and installs a HUP handler that reloads readiness from disk; {@code sync} sends
 * {@code SIGHUP} to that PID after a sync completes. Only valid on Linux/macOS.
 */
public final class ServeNotifier {

    private static final String PID_FILE_NAME = ".codesearch-serve.pid";

    private ServeNotifier() {}

    /**
     * Returns the PID file path for a workspace root.
     *
     * @param workspaceRoot the resolved workspace root
     * @return the PID file path
     */
    public static Path pidFile(Path workspaceRoot) {
        return workspaceRoot.resolve(PID_FILE_NAME);
    }

    /**
     * Writes the current process PID to the PID file.
     *
     * @param pidFile the PID file path
     * @throws IOException if the file cannot be written
     */
    public static void writeServePid(Path pidFile) throws IOException {
        Files.createDirectories(pidFile.toAbsolutePath().getParent());
        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()));
    }

    /**
     * Deletes the PID file, ignoring errors.
     *
     * @param pidFile the PID file path
     */
    public static void deletePid(Path pidFile) {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException e) {
            // best effort
        }
    }

    /**
     * Installs a {@code SIGHUP} handler that runs {@code onReload}. No-op if the platform rejects the
     * signal.
     *
     * @param onReload the action to run on each {@code SIGHUP}
     */
    public static void registerReloadHandler(Runnable onReload) {
        // Uses sun.misc.Signal reflectively so the non-standard API is not referenced at compile
        // time (ADR-0004). Any failure degrades gracefully to a no-op.
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");
            Object hup = signalClass.getConstructor(String.class).newInstance("HUP");
            Object handler = Proxy.newProxyInstance(
                    handlerClass.getClassLoader(), new Class<?>[] {handlerClass}, (proxy, method, args) -> {
                        if ("handle".equals(method.getName())) {
                            onReload.run();
                        }
                        return null;
                    });
            signalClass.getMethod("handle", signalClass, handlerClass).invoke(null, hup, handler);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Signal handling unavailable on this platform; skip.
        }
    }

    /**
     * Sends {@code SIGHUP} to the PID recorded in the PID file, without using a shell.
     *
     * @param pidFile the PID file path
     * @param runner the command runner used to invoke {@code kill}
     * @return {@code true} if a signal was sent successfully
     */
    public static boolean notifyServe(Path pidFile, CommandRunner runner) {
        if (!Files.isRegularFile(pidFile)) {
            return false;
        }
        String pid;
        try {
            pid = Files.readString(pidFile).strip();
        } catch (IOException e) {
            return false;
        }
        if (pid.isEmpty()) {
            return false;
        }
        CommandResult result = runner.run(List.of("kill", "-HUP", pid), null, Map.of(), Duration.ofSeconds(5));
        return result.succeeded();
    }
}
