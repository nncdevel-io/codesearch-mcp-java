package io.nncdevel.codesearch.repo;

import java.time.Instant;

/**
 * Point-in-time sync status of a repository (spec §4.5 catalog status).
 *
 * @param repository the repository id
 * @param state the readiness state
 * @param lastSyncAt time of the last successful sync, or {@code null}
 * @param lastOutcome outcome of the last attempt, or {@code null} if never attempted
 * @param lastError message of the last failure, or {@code null} if the last attempt succeeded
 * @param lastCommit short commit hash currently checked out, or {@code null}
 */
public record RepositoryStatus(
        String repository,
        SyncState state,
        Instant lastSyncAt,
        SyncOutcome lastOutcome,
        String lastError,
        String lastCommit) {

    /**
     * Returns an initial status for a repository whose workspace does not yet exist.
     *
     * @param repository the repository id
     * @return an {@link SyncState#UNINITIALIZED} status with all timestamps null
     */
    public static RepositoryStatus uninitialized(String repository) {
        return new RepositoryStatus(repository, SyncState.UNINITIALIZED, null, null, null, null);
    }

    /**
     * Returns a copy marking a successful sync.
     *
     * @param at the time the sync completed
     * @param commit the commit hash now checked out, or {@code null} if unavailable
     * @return a {@link SyncState#READY} status recording the success
     */
    public RepositoryStatus withSuccess(Instant at, String commit) {
        return new RepositoryStatus(repository, SyncState.READY, at, SyncOutcome.SUCCESS, null, commit);
    }

    /**
     * Returns a copy marking a failed sync. A repository that was already {@link SyncState#READY}
     * stays {@code READY} (its existing workspace remains usable); otherwise it becomes {@link
     * SyncState#FAILED}.
     *
     * @param error the failure message (must not contain secrets)
     * @return a status recording the failure
     */
    public RepositoryStatus withFailure(String error) {
        SyncState newState = state == SyncState.READY ? SyncState.READY : SyncState.FAILED;
        return new RepositoryStatus(repository, newState, lastSyncAt, SyncOutcome.FAILURE, error, lastCommit);
    }
}
