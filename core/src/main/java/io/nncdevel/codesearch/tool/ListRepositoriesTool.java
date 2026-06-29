package io.nncdevel.codesearch.tool;

import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.repo.RepositoryManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@code list_repositories}: returns the configured repository catalog for discovery by
 * hosts that do not inject Resources into the model context (spec §4.5).
 */
public final class ListRepositoriesTool {

    private final RepositoryManager repositories;

    /**
     * Constructs the tool.
     *
     * @param context the shared tool collaborators
     */
    public ListRepositoriesTool(ToolContext context) {
        this.repositories = context.repositories();
    }

    /**
     * Returns the repository catalog in configuration order.
     *
     * @return the catalog, with each repository's current status
     */
    public RepositoriesCatalog list() {
        List<RepositoryCatalogEntry> entries = new ArrayList<>();
        for (RepositoryConfig repo : repositories.all()) {
            entries.add(entryFor(repo));
        }
        return new RepositoriesCatalog(entries);
    }

    /**
     * Builds the catalog entry for a single repository (also used by the repository Resource).
     *
     * @param repo the repository configuration
     * @return the catalog entry with current status
     */
    public RepositoryCatalogEntry entryFor(RepositoryConfig repo) {
        return new RepositoryCatalogEntry(
                repo.id(),
                repo.branch(),
                repo.hosting().wire(),
                repo.hostingBaseUrl(),
                repo.excludePaths(),
                repo.refreshIntervalSeconds(),
                repo.description(),
                RepositoryStatusView.of(repositories.status(repo.id())));
    }
}
