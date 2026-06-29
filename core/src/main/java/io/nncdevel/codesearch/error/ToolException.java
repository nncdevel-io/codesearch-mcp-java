package io.nncdevel.codesearch.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A domain error carrying an {@link ErrorCode}, a human-readable message, and optional structured
 * details.
 *
 * <p>The message must be English, a single sentence, and must not end with a period (spec §5.3).
 * Details must never contain secrets or stack traces.
 */
public final class ToolException extends RuntimeException {

    /** The error code identifying the failure category. */
    private final ErrorCode code;

    /** Insertion-ordered, immutable supplementary fields. */
    private final Map<String, Object> details;

    /**
     * Constructs an error with no structured details.
     *
     * @param code the error code
     * @param message the human-readable message (English, one sentence, no trailing period)
     */
    public ToolException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    /**
     * Constructs an error with structured details.
     *
     * @param code the error code
     * @param message the human-readable message (English, one sentence, no trailing period)
     * @param details supplementary fields; must not contain secrets or stack traces
     */
    public ToolException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    /**
     * Returns the error code.
     *
     * @return the error code
     */
    public ErrorCode code() {
        return code;
    }

    /**
     * Returns an unmodifiable, insertion-ordered view of the error details.
     *
     * @return the error details (possibly empty)
     */
    public Map<String, Object> details() {
        return details;
    }
}
