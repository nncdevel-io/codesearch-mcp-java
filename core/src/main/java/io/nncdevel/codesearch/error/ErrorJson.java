package io.nncdevel.codesearch.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializes a {@link ToolException} into the {@code {"code","message","details"}} JSON string used
 * as the text content of an {@code isError} tool result (spec §5.1).
 */
public final class ErrorJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ErrorJson() {}

    /**
     * Serializes the error with a stable key order: {@code code}, {@code message}, {@code details}.
     *
     * @param error the domain error to serialize
     * @return the JSON string used as the {@code isError} tool-result text content
     */
    public static String serialize(ToolException error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", error.code().code());
        payload.put("message", error.getMessage());
        payload.put("details", error.details());
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // The payload is composed solely of strings and a plain map, so this is unreachable.
            throw new IllegalStateException("failed to serialize error payload", e);
        }
    }
}
