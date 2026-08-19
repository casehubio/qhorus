package io.casehub.a2a.jsonrpc;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcRequestTest {

    @Test
    void builder_createsValidJsonRpc2Request() {
        ObjectNode node = JsonRpcRequest.builder("message/send")
            .id("1")
            .param("contextId", "test-channel")
            .toJsonNode();

        assertThat(node.get("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(node.get("id").asText()).isEqualTo("1");
        assertThat(node.get("method").asText()).isEqualTo("message/send");
        assertThat(node.get("params").get("contextId").asText()).isEqualTo("test-channel");
    }

    @Test
    void builder_withoutId_noIdField() {
        ObjectNode node = JsonRpcRequest.builder("tasks/cancel")
            .toJsonNode();

        assertThat(node.has("id")).isFalse();
        assertThat(node.get("method").asText()).isEqualTo("tasks/cancel");
    }

    @Test
    void build_createsRecord() {
        JsonRpcRequest request = JsonRpcRequest.builder("tasks/get")
            .id("42")
            .param("id", "task-1")
            .build();

        assertThat(request.jsonrpc()).isEqualTo("2.0");
        assertThat(request.id()).isEqualTo("42");
        assertThat(request.method()).isEqualTo("tasks/get");
        assertThat(request.params().get("id").asText()).isEqualTo("task-1");
    }
}
