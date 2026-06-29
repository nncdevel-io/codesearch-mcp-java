package io.nncdevel.codesearch.repo;

/** Outcome of the most recent sync attempt (spec §4.5). */
public enum SyncOutcome {
    /** The last sync attempt succeeded. */
    SUCCESS("success"),
    /** The last sync attempt failed. */
    FAILURE("failure");

    private final String wire;

    SyncOutcome(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the lowercase wire value used in tool and resource output.
     *
     * @return the wire value (e.g. {@code "success"})
     */
    public String wire() {
        return wire;
    }
}
