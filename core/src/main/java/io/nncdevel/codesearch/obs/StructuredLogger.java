package io.nncdevel.codesearch.obs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits one structured (JSON) log line per event via SLF4J, masking secrets through {@link
 * Redaction} (requirements §5.6, §5.4). Each line carries an {@code event} name plus the supplied
 * fields.
 */
public final class StructuredLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Logger delegate;

    private StructuredLogger(Logger delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates a structured logger for the given category.
     *
     * @param category the SLF4J logger name
     * @return a structured logger
     */
    public static StructuredLogger forCategory(String category) {
        return new StructuredLogger(LoggerFactory.getLogger(category));
    }

    /**
     * Logs an event at INFO level.
     *
     * @param event the event name
     * @param fields the event fields (redacted before emission)
     */
    public void info(String event, Map<String, Object> fields) {
        delegate.info(render(event, fields));
    }

    /**
     * Logs an event at WARN level.
     *
     * @param event the event name
     * @param fields the event fields (redacted before emission)
     */
    public void warn(String event, Map<String, Object> fields) {
        delegate.warn(render(event, fields));
    }

    /**
     * Logs an event at DEBUG level, skipping serialization entirely when DEBUG is disabled.
     *
     * @param event the event name
     * @param fields the event fields (redacted before emission)
     */
    public void debug(String event, Map<String, Object> fields) {
        if (delegate.isDebugEnabled()) {
            delegate.debug(render(event, fields));
        }
    }

    /**
     * Returns whether DEBUG output is enabled (used to guard expensive payload assembly).
     *
     * @return {@code true} if DEBUG is enabled
     */
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    private static String render(String event, Map<String, Object> fields) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        if (fields != null) {
            for (Map.Entry<String, Object> e : fields.entrySet()) {
                payload.put(e.getKey(), Redaction.isSensitive(e.getKey()) ? "***" : Redaction.redact(e.getValue()));
            }
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"event\":\"" + event + "\",\"log_error\":\"serialization_failed\"}";
        }
    }
}
