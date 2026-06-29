package io.nncdevel.codesearch.tool;

import java.util.List;

/**
 * One entry of the repository catalog returned by {@code list_repositories} and the repository
 * Resource (spec §4.5.2).
 *
 * @param id the repository id
 * @param branch the reference branch
 * @param hosting the hosting service wire value
 * @param hostingBaseUrl the base URL used for Git URLs
 * @param excludePaths the configured exclusion prefixes
 * @param refreshIntervalSeconds the periodic sync interval
 * @param description the free-form description, or {@code null}
 * @param status the current sync status
 */
public record RepositoryCatalogEntry(
        String id,
        String branch,
        String hosting,
        String hostingBaseUrl,
        List<String> excludePaths,
        long refreshIntervalSeconds,
        String description,
        RepositoryStatusView status) {

    /** Defensively copies the exclusion list. */
    public RepositoryCatalogEntry {
        excludePaths = List.copyOf(excludePaths);
    }
}
