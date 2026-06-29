package io.nncdevel.codesearch.backend;

import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists Git-tracked files via {@code git ls-files} for {@code list_tree} (spec §4.3). Untracked and
 * ignored files are excluded by git's default behavior.
 */
public final class GitLsBackend {

    private final CommandRunner runner;

    /**
     * Constructs a backend that runs git through the given runner.
     *
     * @param runner the command runner used to invoke git
     */
    public GitLsBackend(CommandRunner runner) {
        this.runner = runner;
    }

    /**
     * Returns repository-relative paths of tracked files under {@code path}.
     *
     * @param workspace the repository workspace directory
     * @param path the repository-relative subpath to list, or empty/{@code null} for the whole repo
     * @param timeout the maximum run time for the git invocation
     * @return the tracked file paths (untracked and ignored files excluded)
     * @throws ToolException with {@link ErrorCode#TIMEOUT} if git times out, or {@link
     *     ErrorCode#BACKEND_FAILURE} if git exits non-zero
     */
    public List<String> listTrackedFiles(Path workspace, String path, Duration timeout) {
        List<String> argv = new ArrayList<>(List.of("git", "-C", workspace.toString(), "ls-files", "-z"));
        if (path != null && !path.isEmpty()) {
            argv.add("--");
            argv.add(path);
        }
        CommandResult r = runner.run(argv, workspace, Map.of(), timeout);
        if (r.timedOut()) {
            throw new ToolException(ErrorCode.TIMEOUT, "Tree listing timed out");
        }
        if (r.exitCode() != 0) {
            throw new ToolException(
                    ErrorCode.BACKEND_FAILURE, "git ls-files failed", Map.of("exit_code", r.exitCode()));
        }
        List<String> files = new ArrayList<>();
        for (String entry : r.stdout().split("\0")) {
            if (!entry.isEmpty()) {
                files.add(entry);
            }
        }
        return files;
    }
}
