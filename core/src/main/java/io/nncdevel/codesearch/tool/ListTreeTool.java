package io.nncdevel.codesearch.tool;

import io.nncdevel.codesearch.backend.GitLsBackend;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.util.PathExclusion;
import io.nncdevel.codesearch.util.PathSafe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@code list_tree}: a depth-limited directory overview built from {@code git ls-files}
 * (spec §4.3). Only Git-tracked files are included.
 */
public final class ListTreeTool {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RepositoryManager repositories;
    private final GitLsBackend gitLs;

    /**
     * Constructs the tool.
     *
     * @param context the shared tool collaborators
     */
    public ListTreeTool(ToolContext context) {
        this.repositories = context.repositories();
        this.gitLs = context.gitLs();
    }

    /**
     * Renders a directory tree rooted at {@code path}.
     *
     * @param repository the repository id
     * @param path the repository-relative root, or {@code null}/empty for the repository root
     * @param maxDepth the maximum depth to render
     * @param showFiles whether to include files
     * @param maxEntries the maximum number of entries before truncating
     * @return the rendered tree and its metadata
     * @throws ToolException with {@link ErrorCode#REPO_NOT_FOUND}, {@link ErrorCode#REPO_NOT_READY},
     *     {@link ErrorCode#INVALID_PATH}, {@link ErrorCode#TIMEOUT}, or {@link
     *     ErrorCode#BACKEND_FAILURE}
     */
    public ListTreeResult list(String repository, String path, int maxDepth, boolean showFiles, int maxEntries) {
        RepositoryConfig repo = repositories.require(repository);
        Path workspace = repositories.requireReadyWorkspace(repository);
        String root = PathSafe.validateRelative(path);

        List<String> tracked = gitLs.listTrackedFiles(workspace, root, TIMEOUT);
        String prefix = root.isEmpty() ? "" : root + "/";
        List<String> relative = new ArrayList<>();
        for (String file : tracked) {
            if (PathExclusion.isExcluded(file, repo.excludePaths())) {
                continue;
            }
            relative.add(file.startsWith(prefix) ? file.substring(prefix.length()) : file);
        }

        TreeResult tree = TreeBuilder.build(relative, maxDepth, showFiles, maxEntries);
        return new ListTreeResult(repo.id(), root, tree.tree(), tree.truncated(), tree.entryCount());
    }
}
