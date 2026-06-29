package io.nncdevel.codesearch.backend;

import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs ripgrep and maps its exit codes (0 = matches, 1 = no matches, &ge;2 = error) to results or
 * {@link ToolException}s (spec §4.1, §5.2).
 */
public final class RipgrepBackend {

    private final CommandRunner runner;

    /**
     * Constructs a backend that runs ripgrep through the given runner.
     *
     * @param runner the command runner used to invoke ripgrep
     */
    public RipgrepBackend(CommandRunner runner) {
        this.runner = runner;
    }

    /**
     * Runs a {@code rg --json} search and returns raw stdout.
     *
     * @param workspace the repository workspace directory
     * @param argv the ripgrep argument vector (see {@link RipgrepArgs#search})
     * @param timeout the maximum run time
     * @return raw {@code --json} stdout, empty when there are no matches
     * @throws ToolException with {@link ErrorCode#TIMEOUT}, {@link ErrorCode#INVALID_PATTERN}, or
     *     {@link ErrorCode#BACKEND_FAILURE} depending on the failure
     */
    public String search(Path workspace, List<String> argv, Duration timeout) {
        CommandResult r = runner.run(argv, workspace, Map.of(), timeout);
        requireOk(r);
        return r.stdout();
    }

    /**
     * Runs {@code rg --files} and returns the matching relative paths.
     *
     * @param workspace the repository workspace directory
     * @param argv the ripgrep argument vector (see {@link RipgrepArgs#listFiles})
     * @param timeout the maximum run time
     * @return the matching repository-relative file paths
     * @throws ToolException with {@link ErrorCode#TIMEOUT}, {@link ErrorCode#INVALID_PATTERN}, or
     *     {@link ErrorCode#BACKEND_FAILURE} depending on the failure
     */
    public List<String> listFiles(Path workspace, List<String> argv, Duration timeout) {
        CommandResult r = runner.run(argv, workspace, Map.of(), timeout);
        requireOk(r);
        List<String> files = new ArrayList<>();
        for (String line : r.stdout().split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                files.add(trimmed.startsWith("./") ? trimmed.substring(2) : trimmed);
            }
        }
        return files;
    }

    private static void requireOk(CommandResult r) {
        if (r.timedOut()) {
            throw new ToolException(ErrorCode.TIMEOUT, "Search timed out");
        }
        // ripgrep: 0 = matches found, 1 = no matches (both are success for us).
        if (r.exitCode() == 0 || r.exitCode() == 1) {
            return;
        }
        String stderr = r.stderr().toLowerCase();
        if (stderr.contains("regex parse error")
                || stderr.contains("error parsing glob")
                || stderr.contains("unrecognized")) {
            throw new ToolException(
                    ErrorCode.INVALID_PATTERN,
                    "Invalid search or glob pattern",
                    Map.of("detail", firstLine(r.stderr())));
        }
        throw new ToolException(ErrorCode.BACKEND_FAILURE, "ripgrep failed", Map.of("exit_code", r.exitCode()));
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s.strip() : s.substring(0, nl).strip();
    }
}
