package io.nncdevel.codesearch.tool;

import io.nncdevel.codesearch.backend.RipgrepArgs;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.util.GitUrl;
import io.nncdevel.codesearch.util.PathExclusion;
import io.nncdevel.codesearch.util.PathSafe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implements {@code list_files}: glob-based file listing via {@code rg --files}, ordered by last
 * modification time descending (spec §4.2).
 */
public final class ListFilesTool {

    /** Hard cap on returned files (spec §5.5). */
    static final int MAX_RESULTS_CAP = 500;

    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RepositoryManager repositories;
    private final RipgrepBackend ripgrep;

    /**
     * Constructs the tool.
     *
     * @param context the shared tool collaborators
     */
    public ListFilesTool(ToolContext context) {
        this.repositories = context.repositories();
        this.ripgrep = context.ripgrep();
    }

    /**
     * Lists files matching {@code pattern}, newest first.
     *
     * @param repository the repository id
     * @param pattern the glob pattern to match
     * @param path the repository-relative search root, or {@code null}/empty for the whole repo
     * @param maxResults the requested maximum number of files
     * @return the matching files and a truncation flag
     * @throws ToolException with {@link ErrorCode#REPO_NOT_FOUND}, {@link ErrorCode#REPO_NOT_READY},
     *     {@link ErrorCode#INVALID_PATH}, {@link ErrorCode#INVALID_PATTERN}, {@link
     *     ErrorCode#TIMEOUT}, or {@link ErrorCode#BACKEND_FAILURE}
     */
    public ListFilesResult list(String repository, String pattern, String path, int maxResults) {
        RepositoryConfig repo = repositories.require(repository);
        Path workspace = repositories.requireReadyWorkspace(repository);
        String root = PathSafe.validateRelative(path);
        int max = Math.min(maxResults <= 0 ? DEFAULT_MAX_RESULTS : maxResults, MAX_RESULTS_CAP);

        List<String> files = ripgrep.listFiles(workspace, RipgrepArgs.listFiles(pattern, root), TIMEOUT);

        List<FileEntry> entries = new ArrayList<>();
        for (String file : files) {
            if (PathExclusion.isExcluded(file, repo.excludePaths())) {
                continue;
            }
            entries.add(new FileEntry(
                    repo.id(),
                    file,
                    lastModified(workspace.resolve(file)),
                    GitUrl.file(repo.hostingBaseUrl(), repo.hosting(), repo.branch(), file)));
        }
        entries.sort(Comparator.comparing((FileEntry e) -> Instant.parse(e.lastModified()))
                .reversed());

        boolean truncated = entries.size() > max;
        List<FileEntry> capped = entries.subList(0, Math.min(max, entries.size()));
        return new ListFilesResult(new ArrayList<>(capped), truncated);
    }

    private static String lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().toString();
        } catch (IOException e) {
            return Instant.EPOCH.toString();
        }
    }
}
