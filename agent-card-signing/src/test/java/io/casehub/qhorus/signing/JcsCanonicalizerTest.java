package io.casehub.qhorus.signing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JcsCanonicalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void canonicalizeSortsKeys() {
        ObjectNode node = mapper.createObjectNode();
        node.put("z", "last");
        node.put("a", "first");
        node.put("m", "middle");

        byte[] result = JcsCanonicalizer.canonicalize(node);
        assertThat(new String(result)).isEqualTo("{\"a\":\"first\",\"m\":\"middle\",\"z\":\"last\"}");
    }

    @Test
    void canonicalizeNormalizesWholeNumberFloats() {
        ObjectNode node = mapper.createObjectNode();
        node.put("value", 1.0);

        byte[] result = JcsCanonicalizer.canonicalize(node);
        assertThat(new String(result)).isEqualTo("{\"value\":1}");
    }

    @Test
    void canonicalizePreservesFractionalNumbers() {
        ObjectNode node = mapper.createObjectNode();
        node.put("value", 1.5);

        byte[] result = JcsCanonicalizer.canonicalize(node);
        assertThat(new String(result)).isEqualTo("{\"value\":1.5}");
    }

    @Test
    void canonicalizeHandlesNestedObjects() {
        ObjectNode inner = mapper.createObjectNode();
        inner.put("b", 2);
        inner.put("a", 1);
        ObjectNode outer = mapper.createObjectNode();
        outer.set("nested", inner);
        outer.put("top", "value");

        byte[] result = JcsCanonicalizer.canonicalize(outer);
        assertThat(new String(result)).isEqualTo("{\"nested\":{\"a\":1,\"b\":2},\"top\":\"value\"}");
    }

    @Test
    void canonicalizeHandlesArrays() {
        ObjectNode node = mapper.createObjectNode();
        ArrayNode arr = mapper.createArrayNode();
        arr.add("c");
        arr.add("a");
        arr.add("b");
        node.set("items", arr);

        byte[] result = JcsCanonicalizer.canonicalize(node);
        assertThat(new String(result)).isEqualTo("{\"items\":[\"c\",\"a\",\"b\"]}");
    }

    @Test
    void canonicalizeHandlesNullValues() {
        ObjectNode node = mapper.createObjectNode();
        node.put("present", "yes");
        node.putNull("absent");

        byte[] result = JcsCanonicalizer.canonicalize(node);
        assertThat(new String(result)).isEqualTo("{\"absent\":null,\"present\":\"yes\"}");
    }

    @Test
    void canonicalizeHandlesBooleans() {
        ObjectNode node = mapper.createObjectNode();
        node.put("flag", true);

        byte[] result = JcsCanonicalizer.canonicalize(node);
        assertThat(new String(result)).isEqualTo("{\"flag\":true}");
    }

    @Test
    void canonicalizeDeterministic() {
        ObjectNode a = mapper.createObjectNode();
        a.put("x", 1);
        a.put("y", 2);

        ObjectNode b = mapper.createObjectNode();
        b.put("y", 2);
        b.put("x", 1);

        assertThat(JcsCanonicalizer.canonicalize(a))
                .isEqualTo(JcsCanonicalizer.canonicalize(b));
    }
}
