package io.nncdevel.codesearch.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.Map;

/**
 * Serializes tool-result records to JSON and to {@code Map} structured content using snake_case
 * property names, matching the wire schema in spec §4.
 */
final class ToolJson {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private ToolJson() {}

    /**
     * Converts a result record to a snake_case-keyed map for MCP structured content.
     *
     * @param value the result record
     * @return the structured-content map
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> toStructured(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }

    /**
     * Serializes a value to a snake_case JSON string for the textual tool-result content.
     *
     * @param value the value to serialize
     * @return the JSON string
     */
    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize tool result", e);
        }
    }
}
