package io.nncdevel.codesearch.config;

/** Thrown when configuration is missing or invalid; aborts startup (spec §7.4). */
public final class ConfigException extends RuntimeException {

    /**
     * Constructs an exception with a message.
     *
     * @param message the validation failure description
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * Constructs an exception with a message and underlying cause.
     *
     * @param message the validation failure description
     * @param cause the underlying I/O or parse error
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
