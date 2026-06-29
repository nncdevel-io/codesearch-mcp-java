package io.nncdevel.codesearch.repo;

/** Readiness state of a repository's local workspace (spec §4.5). */
public enum SyncState {
    /** No workspace exists yet; the initial clone has not completed. */
    UNINITIALIZED("uninitialized"),
    /** The workspace exists and can serve searches. */
    READY("ready"),
    /** The initial clone failed; no usable workspace exists. */
    FAILED("failed");

    private final String wire;

    SyncState(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the lowercase wire value used in tool and resource output.
     *
     * @return the wire value (e.g. {@code "ready"})
     */
    public String wire() {
        return wire;
    }
}
