package io.nncdevel.codesearch.tool;

import io.nncdevel.codesearch.backend.RgMatch;
import io.nncdevel.codesearch.backend.RipgrepArgs;
import io.nncdevel.codesearch.backend.RipgrepBackend;
import io.nncdevel.codesearch.backend.RipgrepJson;
import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.error.ErrorCode;
import io.nncdevel.codesearch.error.ToolException;
import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.util.GitUrl;
import io.nncdevel.codesearch.util.PathExclusion;
import io.nncdevel.codesearch.util.PathSafe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements {@code search_code}: regex content search via ripgrep, assembled into one of three
 * output modes with Git URLs and truncation (spec §4.1).
 */
public final class SearchCodeTool {

    /** Hard cap on returned results (spec §5.5). */
    static final int MAX_RESULTS_CAP = 500;

    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RepositoryManager repositories;
    private final RipgrepBackend ripgrep;

    /**
     * Constructs the tool.
     *
     * @param context the shared tool collaborators
     */
    public SearchCodeTool(ToolContext context) {
        this.repositories = context.repositories();
        this.ripgrep = context.ripgrep();
    }

    /**
     * Runs a search and assembles the result for the requested output mode.
     *
     * @param request the validated search input
     * @return the mode-specific result
     * @throws ToolException with {@link ErrorCode#REPO_NOT_FOUND}, {@link ErrorCode#REPO_NOT_READY},
     *     {@link ErrorCode#INVALID_PATH}, {@link ErrorCode#INVALID_PATTERN}, {@link
     *     ErrorCode#TIMEOUT}, or {@link ErrorCode#BACKEND_FAILURE}
     */
    public SearchResult search(SearchRequest request) {
        RepositoryConfig repo = repositories.require(request.repository());
        var workspace = repositories.requireReadyWorkspace(request.repository());
        String path = PathSafe.validateRelative(request.path());
        int max = Math.min(request.maxResults() <= 0 ? DEFAULT_MAX_RESULTS : request.maxResults(), MAX_RESULTS_CAP);
        boolean includeContext = request.outputMode() == OutputMode.CONTENT
                && (request.contextBefore() > 0 || request.contextAfter() > 0);

        List<String> argv = RipgrepArgs.search(
                request.pattern(),
                request.glob(),
                request.type(),
                request.caseSensitive(),
                request.contextBefore(),
                request.contextAfter(),
                includeContext,
                path);
        String stdout = ripgrep.search(workspace, argv, TIMEOUT);
        List<RgMatch> matches =
                RipgrepJson.parseMatches(stdout, request.contextBefore(), request.contextAfter()).stream()
                        .filter(m -> !PathExclusion.isExcluded(m.file(), repo.excludePaths()))
                        .toList();

        return switch (request.outputMode()) {
            case CONTENT -> content(repo, matches, max);
            case FILES_WITH_MATCHES -> files(repo, matches, max);
            case COUNT -> count(repo, matches, max);
        };
    }

    private static SearchResult content(RepositoryConfig repo, List<RgMatch> matches, int max) {
        int total = matches.size();
        boolean truncated = total > max;
        List<SearchMatch> out = new ArrayList<>();
        for (RgMatch m : matches.subList(0, Math.min(max, total))) {
            int start = m.contextBefore().isEmpty()
                    ? m.lineNumber()
                    : m.contextBefore().get(0).lineNumber();
            int end = m.contextAfter().isEmpty()
                    ? m.lineNumber()
                    : m.contextAfter().get(m.contextAfter().size() - 1).lineNumber();
            String url = end > start
                    ? GitUrl.range(repo.hostingBaseUrl(), repo.hosting(), repo.branch(), m.file(), start, end)
                    : GitUrl.singleLine(repo.hostingBaseUrl(), repo.hosting(), repo.branch(), m.file(), m.lineNumber());
            out.add(new SearchMatch(
                    repo.id(),
                    m.file(),
                    m.lineNumber(),
                    m.lineContent(),
                    m.contextBefore().stream()
                            .map(c -> new ContextLine(c.lineNumber(), c.content()))
                            .toList(),
                    m.contextAfter().stream()
                            .map(c -> new ContextLine(c.lineNumber(), c.content()))
                            .toList(),
                    url));
        }
        return new SearchResult.Content(out, truncated, total);
    }

    private static SearchResult files(RepositoryConfig repo, List<RgMatch> matches, int max) {
        List<String> distinct = matches.stream().map(RgMatch::file).distinct().toList();
        boolean truncated = distinct.size() > max;
        List<FileHit> out = new ArrayList<>();
        for (String file : distinct.subList(0, Math.min(max, distinct.size()))) {
            out.add(new FileHit(
                    repo.id(), file, GitUrl.file(repo.hostingBaseUrl(), repo.hosting(), repo.branch(), file)));
        }
        return new SearchResult.Files(out, truncated);
    }

    private static SearchResult count(RepositoryConfig repo, List<RgMatch> matches, int max) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RgMatch m : matches) {
            counts.merge(m.file(), 1, Integer::sum);
        }
        boolean truncated = counts.size() > max;
        List<CountHit> out = new ArrayList<>();
        int taken = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (taken++ >= max) {
                break;
            }
            out.add(new CountHit(
                    repo.id(),
                    e.getKey(),
                    e.getValue(),
                    GitUrl.file(repo.hostingBaseUrl(), repo.hosting(), repo.branch(), e.getKey())));
        }
        return new SearchResult.Count(out, truncated);
    }
}
