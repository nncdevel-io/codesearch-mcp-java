package io.nncdevel.codesearch.config;

/** Authentication type for a repository's Git remote (spec §7.2). */
public enum AuthType {
    /** Personal access token supplied as the HTTPS password. */
    TOKEN("token"),
    /** SSH private key referenced by path. */
    SSH_KEY("ssh_key"),
    /** Anonymous access; no credentials. */
    NONE("none");

    private final String wire;

    AuthType(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the lowercase wire value used in configuration.
     *
     * @return the wire value (e.g. {@code "token"})
     */
    public String wire() {
        return wire;
    }

    /**
     * Parses the lowercase wire value.
     *
     * @param value the wire value to parse
     * @return the matching auth-type constant
     * @throws IllegalArgumentException if {@code value} matches no known auth type
     */
    public static AuthType fromWire(String value) {
        for (AuthType a : values()) {
            if (a.wire.equals(value)) {
                return a;
            }
        }
        throw new IllegalArgumentException("unknown auth_type: " + value);
    }
}
