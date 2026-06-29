package io.nncdevel.codesearch.obs;

import io.nncdevel.codesearch.repo.RepositoryManager;
import io.nncdevel.codesearch.repo.RepositoryStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes and logs each repository's last sync status (requirements §5.6). Provides an ops-facing
 * snapshot and a structured log emission.
 */
public final class Observability {

    private Observability() {}

    /**
     * Builds an ordered snapshot of every repository's sync status, with enums rendered as wire
     * values and instants as ISO-8601 strings.
     *
     * @param manager the repository manager to read statuses from
     * @return one ordered field map per repository, in catalog order
     */
    public static List<Map<String, Object>> snapshot(RepositoryManager manager) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (RepositoryStatus status : manager.statuses()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("repository", status.repository());
            fields.put("state", status.state().wire());
            fields.put(
                    "last_sync_at",
                    status.lastSyncAt() == null ? null : status.lastSyncAt().toString());
            fields.put(
                    "last_outcome",
                    status.lastOutcome() == null ? null : status.lastOutcome().wire());
            fields.put("last_error", status.lastError());
            fields.put("last_commit", status.lastCommit());
            out.add(fields);
        }
        return out;
    }

    /**
     * Logs one {@code sync_status} event per repository at INFO level.
     *
     * @param logger the structured logger to emit through
     * @param manager the repository manager to read statuses from
     */
    public static void logStatus(StructuredLogger logger, RepositoryManager manager) {
        for (Map<String, Object> fields : snapshot(manager)) {
            logger.info("sync_status", fields);
        }
    }
}
