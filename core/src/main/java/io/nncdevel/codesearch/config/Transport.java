package io.nncdevel.codesearch.config;

/** MCP transport selection for the standalone runtime (spec §2.1, §7.3). */
public enum Transport {
    /** Standard input/output transport. */
    STDIO("stdio"),
    /** Streamable HTTP transport. */
    HTTP("http");

    private final String wire;

    Transport(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the lowercase wire value used in configuration.
     *
     * @return the wire value (e.g. {@code "stdio"})
     */
    public String wire() {
        return wire;
    }

    /**
     * Parses the lowercase wire value.
     *
     * @param value the wire value to parse
     * @return the matching transport constant
     * @throws IllegalArgumentException if {@code value} matches no known transport
     */
    public static Transport fromWire(String value) {
        for (Transport t : values()) {
            if (t.wire.equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown transport: " + value);
    }
}
