package io.nncdevel.codesearch.config;

import java.util.Map;

/**
 * Parsed {@code secrets.json}, keyed by repository id (spec §7.2).
 *
 * @param byRepositoryId secret entries keyed by repository id
 */
public record SecretsFile(Map<String, SecretEntry> byRepositoryId) {

    /** Defensively copies the entry map. */
    public SecretsFile {
        byRepositoryId = Map.copyOf(byRepositoryId);
    }

    /**
     * Returns an empty secrets set (anonymous access for all repositories).
     *
     * @return an empty {@code SecretsFile}
     */
    public static SecretsFile empty() {
        return new SecretsFile(Map.of());
    }

    /**
     * Returns the entry for the repository, or {@link SecretEntry#none()} if absent.
     *
     * @param repositoryId the repository id to look up
     * @return the matching secret entry, or {@link SecretEntry#none()} when none is configured
     */
    public SecretEntry forRepository(String repositoryId) {
        return byRepositoryId.getOrDefault(repositoryId, SecretEntry.none());
    }
}
