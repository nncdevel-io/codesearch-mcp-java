package io.nncdevel.codesearch.error;

/**
 * Domain error codes returned as {@code isError} tool results.
 *
 * <p>See {@code docs/devel/specs/spec.md} §5.2. The {@link #code()} string is the stable wire
 * value embedded in the {@code {"code": ...}} JSON payload.
 */
public enum ErrorCode {
    /** The requested repository is not configured. */
    REPO_NOT_FOUND,
    /** The repository's initial clone has not completed yet. */
    REPO_NOT_READY,
    /** Path traversal or an absolute path was detected. */
    INVALID_PATH,
    /** The given path does not exist inside the repository. */
    PATH_NOT_FOUND,
    /** The search or glob pattern is invalid. */
    INVALID_PATTERN,
    /** The target file exceeds the size limit. */
    FILE_TOO_LARGE,
    /** The target file cannot be decoded as text. */
    FILE_BINARY,
    /** The tool exceeded its time budget. */
    TIMEOUT,
    /** ripgrep or git terminated abnormally. */
    BACKEND_FAILURE,
    /** An internal server error not covered by the codes above. */
    INTERNAL_ERROR;

    /**
     * Returns the stable wire value, which equals the enum constant name.
     *
     * @return the wire value embedded in the error JSON {@code code} field
     */
    public String code() {
        return name();
    }
}
