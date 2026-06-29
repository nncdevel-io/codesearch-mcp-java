package io.nncdevel.codesearch.tool;

import java.util.List;

/**
 * Output of the {@code list_repositories} tool (spec §4.5.2).
 *
 * @param repositories the configured repositories, in catalog order
 */
public record RepositoriesCatalog(List<RepositoryCatalogEntry> repositories) {

    /** Defensively copies the entry list. */
    public RepositoriesCatalog {
        repositories = List.copyOf(repositories);
    }
}
