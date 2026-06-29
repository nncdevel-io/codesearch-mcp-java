package io.nncdevel.codesearch.config;

import java.util.List;

/**
 * Parsed {@code repos.json} content (spec §7.1).
 *
 * @param workspaceRoot optional clone root override; may be {@code null}
 * @param secretsPath optional secrets path declared in {@code repos.json}; may be {@code null}
 * @param repositories the configured repositories, in file order
 */
public record RepositoriesFile(String workspaceRoot, String secretsPath, List<RepositoryConfig> repositories) {

    /** Defensively copies the repository list. */
    public RepositoriesFile {
        repositories = List.copyOf(repositories);
    }
}
