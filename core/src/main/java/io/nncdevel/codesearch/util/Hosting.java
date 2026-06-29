package io.nncdevel.codesearch.util;

/** Supported Git hosting services for URL generation (spec §6). */
public enum Hosting {
    /** GitHub-style blob URLs ({@code /blob/<branch>/<path>#L<n>}). */
    GITHUB("github"),
    /** GitLab-style blob URLs ({@code /-/blob/<branch>/<path>#L<n>}). */
    GITLAB("gitlab"),
    /** Bitbucket-style source URLs ({@code /src/<branch>/<path>#lines-<n>}). */
    BITBUCKET("bitbucket"),
    /** Gitea-style source URLs ({@code /src/branch/<branch>/<path>#L<n>}). */
    GITEA("gitea");

    private final String wire;

    Hosting(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the lowercase wire value used in configuration and tool output.
     *
     * @return the wire value (e.g. {@code "github"})
     */
    public String wire() {
        return wire;
    }

    /**
     * Parses the lowercase wire value.
     *
     * @param value the wire value to parse
     * @return the matching hosting constant
     * @throws IllegalArgumentException if {@code value} matches no known hosting
     */
    public static Hosting fromWire(String value) {
        for (Hosting h : values()) {
            if (h.wire.equals(value)) {
                return h;
            }
        }
        throw new IllegalArgumentException("unknown hosting: " + value);
    }
}
