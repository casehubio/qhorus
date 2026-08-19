package io.casehub.a2a.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcResponseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void successResponse_isNotError() {
        ObjectNode result = MAPPER.createObjectNode().put("id", "task-1");
        JsonRpcResponse response = new JsonRpcResponse("2.0", "1", result, null);

        assertThat(response.isError()).isFalse();
        assertThat(response.result().get("id").asText()).isEqualTo("task-1");
    }

    @Test
    void errorResponse_isError() {
        ObjectNode error = JsonRpcError.METHOD_NOT_FOUND.toJsonNode(MAPPER, "tasks/unknown");
        JsonRpcResponse response = new JsonRpcResponse("2.0", "1", null, error);

        assertThat(response.isError()).isTrue();
        assertThat(response.errorCode()).isEqualTo(-32601);
        assertThat(response.errorMessage()).isEqualTo("Method not found");
        assertThat(response.errorData()).isEqualTo("tasks/unknown");
    }

    @Test
    void errorResponse_noData_returnsNull() {
        ObjectNode error = JsonRpcError.PARSE_ERROR.toJsonNode(MAPPER);
        JsonRpcResponse response = new JsonRpcResponse("2.0", "1", null, error);

        assertThat(response.errorData()).isNull();
    }
}
