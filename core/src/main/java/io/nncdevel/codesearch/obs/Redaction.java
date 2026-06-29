package io.nncdevel.codesearch.obs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recursively masks secret-bearing values in structured log payloads (requirements §5.4). A field
 * is redacted when its key contains a sensitive token such as {@code token}, {@code secret},
 * {@code password}, {@code authorization}, or {@code ssh_key}.
 */
public final class Redaction {

    private static final String MASK = "***";
    private static final List<String> SENSITIVE =
            List.of("token", "secret", "password", "passwd", "authorization", "ssh_key");

    private Redaction() {}

    /**
     * Returns a deep copy of {@code value} with sensitive map entries masked.
     *
     * @param value a map, list, or scalar from a log payload
     * @return the redacted copy (scalars are returned unchanged)
     */
    public static Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                out.put(key, isSensitive(key) ? MASK : redact(e.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(redact(item));
            }
            return out;
        }
        return value;
    }

    /**
     * Tests whether a key names a sensitive field.
     *
     * @param key the field key
     * @return {@code true} if the key contains a sensitive token
     */
    public static boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String s : SENSITIVE) {
            if (lower.contains(s)) {
                return true;
            }
        }
        return false;
    }
}
