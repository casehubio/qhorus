package io.casehub.a2a.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcErrorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void standardCodes() {
        assertThat(JsonRpcError.PARSE_ERROR.code()).isEqualTo(-32700);
        assertThat(JsonRpcError.INVALID_REQUEST.code()).isEqualTo(-32600);
        assertThat(JsonRpcError.METHOD_NOT_FOUND.code()).isEqualTo(-32601);
        assertThat(JsonRpcError.INVALID_PARAMS.code()).isEqualTo(-32602);
        assertThat(JsonRpcError.INTERNAL_ERROR.code()).isEqualTo(-32603);
    }

    @Test
    void toJsonNode_containsCodeAndMessage() {
        ObjectNode node = JsonRpcError.METHOD_NOT_FOUND.toJsonNode(MAPPER, "unknown method");
        assertThat(node.get("code").asInt()).isEqualTo(-32601);
        assertThat(node.get("message").asText()).isEqualTo("Method not found");
        assertThat(node.get("data").asText()).isEqualTo("unknown method");
    }

    @Test
    void toJsonNode_withoutData_noDataField() {
        ObjectNode node = JsonRpcError.PARSE_ERROR.toJsonNode(MAPPER);
        assertThat(node.get("code").asInt()).isEqualTo(-32700);
        assertThat(node.get("message").asText()).isEqualTo("Parse error");
        assertThat(node.has("data")).isFalse();
    }
}
