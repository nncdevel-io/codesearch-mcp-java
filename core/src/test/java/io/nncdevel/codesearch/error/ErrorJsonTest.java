package io.nncdevel.codesearch.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void serializesCodeMessageAndDetailsInOrder() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("repository", "main-app");
        ToolException error =
                new ToolException(ErrorCode.REPO_NOT_FOUND, "Repository 'main-app' is not configured", details);

        String json = ErrorJson.serialize(error);

        assertTrue(json.indexOf("\"code\"") < json.indexOf("\"message\""), "code before message");
        assertTrue(json.indexOf("\"message\"") < json.indexOf("\"details\""), "message before details");
    }

    @Test
    void serializesExpectedValues() throws Exception {
        ToolException error =
                new ToolException(ErrorCode.INVALID_PATH, "Invalid path", Map.of("path", "../etc/passwd"));

        JsonNode node = MAPPER.readTree(ErrorJson.serialize(error));

        assertEquals("INVALID_PATH", node.get("code").asText());
        assertEquals("Invalid path", node.get("message").asText());
        assertEquals("../etc/passwd", node.get("details").get("path").asText());
    }

    @Test
    void emptyDetailsSerializeAsEmptyObject() throws Exception {
        ToolException error = new ToolException(ErrorCode.TIMEOUT, "Tool timed out");
        JsonNode node = MAPPER.readTree(ErrorJson.serialize(error));
        assertTrue(node.get("details").isObject());
        assertEquals(0, node.get("details").size());
    }

    @Test
    void detailsAreImmutable() {
        ToolException error = new ToolException(ErrorCode.TIMEOUT, "Tool timed out", Map.of("a", 1));
        assertThrows(UnsupportedOperationException.class, () -> error.details().put("b", 2));
    }

    @Test
    void everyCodeWireValueEqualsName() {
        for (ErrorCode code : ErrorCode.values()) {
            assertEquals(code.name(), code.code());
        }
    }
}
