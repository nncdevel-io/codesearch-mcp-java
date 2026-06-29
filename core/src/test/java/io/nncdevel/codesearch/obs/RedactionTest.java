package io.nncdevel.codesearch.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedactionTest {

    @Test
    void masksSensitiveKeysAtAnyDepth() {
        Map<String, Object> input = Map.of(
                "repository",
                "r1",
                "auth",
                Map.of("token", "ghp_secret", "auth_type", "token"),
                "items",
                List.of(Map.of("ssh_key_path", "/k"), Map.of("name", "ok")));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) Redaction.redact(input);

        assertEquals("r1", out.get("repository"));
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) out.get("auth");
        assertEquals("***", auth.get("token"));
        assertEquals("token", auth.get("auth_type"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertEquals("***", items.get(0).get("ssh_key_path"));
        assertEquals("ok", items.get(1).get("name"));
    }

    @Test
    void detectsSensitiveKeys() {
        assertTrue(Redaction.isSensitive("token"));
        assertTrue(Redaction.isSensitive("Authorization"));
        assertTrue(Redaction.isSensitive("ssh_key_path"));
        assertTrue(!Redaction.isSensitive("repository"));
    }

    @Test
    void scalarsPassThrough() {
        assertEquals(42, Redaction.redact(42));
        assertEquals("plain", Redaction.redact("plain"));
    }
}
