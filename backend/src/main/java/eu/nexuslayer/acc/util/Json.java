package eu.nexuslayer.acc.util;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

/** Small shared Jackson facade so parsing failures never escape as raw exceptions. */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize value of type "
                    + (value == null ? "null" : value.getClass().getName()), e);
        }
    }

    public static JsonNode read(String raw) {
        if (raw == null || raw.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            return NullNode.getInstance();
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String raw) {
        try {
            return MAPPER.readValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asText();
    }
}
