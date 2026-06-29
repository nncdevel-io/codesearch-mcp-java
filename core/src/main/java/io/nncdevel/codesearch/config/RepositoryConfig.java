package io.nncdevel.codesearch.config;

import io.nncdevel.codesearch.util.Hosting;
import java.util.List;

/**
 * A single repository entry from {@code repos.json} (spec §7.1).
 *
 * @param id the unique repository identifier
 * @param remote the Git remote URL (SSH or HTTPS)
 * @param branch the reference branch to clone and search
 * @param hosting the hosting service used for Git URL generation
 * @param hostingBaseUrl the base URL used to build Git URLs
 * @param refreshIntervalSeconds the periodic sync interval in seconds
 * @param excludePaths repository-relative path prefixes excluded from search; never {@code null}
 * @param description free-form text describing the repository; may be {@code null}
 */
public record RepositoryConfig(
        String id,
        String remote,
        String branch,
        Hosting hosting,
        String hostingBaseUrl,
        long refreshIntervalSeconds,
        List<String> excludePaths,
        String description) {

    /** Normalizes {@code excludePaths} to a non-null immutable list. */
    public RepositoryConfig {
        excludePaths = excludePaths == null ? List.of() : List.copyOf(excludePaths);
    }
}
