package io.nncdevel.codesearch.tool;

/** Output mode for {@code search_code} (spec §4.1.1). */
public enum OutputMode {
    /** Return matching lines with optional context. */
    CONTENT("content"),
    /** Return only the paths of files that contain a match. */
    FILES_WITH_MATCHES("files_with_matches"),
    /** Return the per-file match count. */
    COUNT("count");

    private final String wire;

    OutputMode(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the lowercase wire value used in tool input.
     *
     * @return the wire value (e.g. {@code "content"})
     */
    public String wire() {
        return wire;
    }

    /**
     * Parses the lowercase wire value, defaulting to {@link #CONTENT} for {@code null}.
     *
     * @param value the wire value, or {@code null} for the default
     * @return the matching mode
     * @throws IllegalArgumentException if {@code value} matches no known mode
     */
    public static OutputMode fromWire(String value) {
        if (value == null) {
            return CONTENT;
        }
        for (OutputMode m : values()) {
            if (m.wire.equals(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("unknown output_mode: " + value);
    }
}
