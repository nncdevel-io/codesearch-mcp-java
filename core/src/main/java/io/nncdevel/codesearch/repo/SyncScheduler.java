package io.nncdevel.codesearch.repo;

import io.nncdevel.codesearch.config.RepositoryConfig;
import io.nncdevel.codesearch.obs.StructuredLogger;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Optional in-process scheduler that periodically syncs each repository on its own interval (design
 * §2 trigger 2). It runs on daemon threads off the request path, so it never blocks tool calls
 * (requirements §5.1). Disabled by default; the runtime starts it only when configured.
 */
public final class SyncScheduler implements AutoCloseable {

    private final GitSync gitSync;
    private final RepositoryManager manager;
    private final StructuredLogger logger;
    private final ScheduledExecutorService executor;

    /**
     * Constructs the scheduler.
     *
     * @param gitSync the sync engine
     * @param manager the repository manager whose statuses are updated
     * @param logger the structured logger for sync events
     */
    public SyncScheduler(GitSync gitSync, RepositoryManager manager, StructuredLogger logger) {
        this.gitSync = gitSync;
        this.manager = manager;
        this.logger = logger;
        this.executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "codesearch-sync-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /** Schedules a periodic sync for every repository at its configured interval. */
    public void start() {
        for (RepositoryConfig repo : manager.all()) {
            long interval = repo.refreshIntervalSeconds();
            executor.scheduleAtFixedRate(() -> runSync(repo), interval, interval, TimeUnit.SECONDS);
        }
    }

    /**
     * Syncs a single repository once and updates its status, isolating any failure.
     *
     * @param repo the repository to sync
     */
    void runSync(RepositoryConfig repo) {
        try {
            RepositoryStatus updated = gitSync.syncOne(
                    repo, manager.secretFor(repo.id()), manager.workspacePath(repo.id()), manager.status(repo.id()));
            manager.updateStatus(repo.id(), updated);
            logger.info(
                    "scheduled_sync",
                    Map.of("repository", repo.id(), "state", updated.state().wire()));
        } catch (RuntimeException e) {
            manager.updateStatus(repo.id(), manager.status(repo.id()).withFailure(e.getMessage()));
            logger.warn(
                    "scheduled_sync_failed", Map.of("repository", repo.id(), "error", String.valueOf(e.getMessage())));
        }
    }

    /** Stops the scheduler and releases its threads. */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
