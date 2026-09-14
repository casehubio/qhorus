package io.casehub.qhorus.signing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

public final class JcsCanonicalizer {

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private JcsCanonicalizer() {}

    public static byte[] canonicalize(ObjectNode node) {
        try {
            Object sorted = sortRecursive(node);
            return CANONICAL_MAPPER.writeValueAsBytes(sorted);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to canonicalize JSON", e);
        }
    }

    private static Object sortRecursive(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                sorted.put(field, sortRecursive(node.get(field)));
            }
            return sorted;
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode element : node) {
                list.add(sortRecursive(element));
            }
            return list;
        } else if (node.isIntegralNumber()) {
            return node.longValue();
        } else if (node.isFloatingPointNumber()) {
            double d = node.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return (long) d;
            }
            return d;
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isNull()) {
            return null;
        } else {
            return node.asText();
        }
    }
}
