package io.nncdevel.codesearch.tool;

import io.nncdevel.codesearch.repo.RepositoryStatus;

/**
 * Serialization view of {@link RepositoryStatus} that renders enums as wire values and instants as
 * ISO-8601 strings (spec §4.5.2 status).
 *
 * @param repository the repository id
 * @param state the readiness state wire value
 * @param lastSyncAt the last successful sync time (ISO-8601), or {@code null}
 * @param lastOutcome the last outcome wire value, or {@code null}
 * @param lastError the last failure message, or {@code null}
 * @param lastCommit the current commit hash, or {@code null}
 */
public record RepositoryStatusView(
        String repository, String state, String lastSyncAt, String lastOutcome, String lastError, String lastCommit) {

    /**
     * Builds a view from a status.
     *
     * @param status the status to render
     * @return the wire-value view
     */
    public static RepositoryStatusView of(RepositoryStatus status) {
        return new RepositoryStatusView(
                status.repository(),
                status.state().wire(),
                status.lastSyncAt() == null ? null : status.lastSyncAt().toString(),
                status.lastOutcome() == null ? null : status.lastOutcome().wire(),
                status.lastError(),
                status.lastCommit());
    }
}
