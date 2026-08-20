package io.casehub.qhorus.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * SSE streaming via JSON-RPC content negotiation at POST /a2a.
 *
 * <p>Accept: text/event-stream routes to dispatchStream(). Only message/send supports streaming;
 * other methods return an error event and close the sink.
 *
 * <p>Refs #396.
 */
@QuarkusTest
@TestProfile(A2AEnabledProfile.class)
class A2AJsonRpcStreamTest {

    @Inject
    QhorusMcpTools tools;

    private static final String A2A_PATH = "/a2a";

    private String jsonRpcRequest(String method, String paramsJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + UUID.randomUUID() + "\",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
    }

    // -----------------------------------------------------------------------
    // SSE method restriction — only message/send supports streaming
    // -----------------------------------------------------------------------

    @Test
    void sseStream_tasksGet_returnsErrorEventAndCloses() {
        String taskId = UUID.randomUUID().toString();
        String body = given()
                .contentType("application/json")
                .accept("text/event-stream")
                .body(jsonRpcRequest("tasks/get", "{\"id\":\"" + taskId + "\"}"))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract().body().asString();

        assertThat(body).contains("event:error");
        assertThat(body).contains("\"final\":true");
    }

    @Test
    void sseStream_unknownMethod_returnsErrorEvent() {
        String body = given()
                .contentType("application/json")
                .accept("text/event-stream")
                .body(jsonRpcRequest("foo/bar", "{}"))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract().body().asString();

        assertThat(body).contains("event:error");
    }

    @Test
    void sseStream_a2aDisabled_returnsErrorEvent() {
        // This test is in the A2AEnabled profile, but we test via A2AResourceDisabledTest
        // Here we just verify streaming with message/send works at all
        tools.createChannel("a2a-rpc-stream-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // First create a task via sync endpoint so it exists
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send",
                        "{\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"stream test\"}]"
                                + ",\"contextId\":\"a2a-rpc-stream-1\",\"taskId\":\"" + taskId + "\"}}"))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200);

        // Resolve the task to terminal state so SSE returns immediately
        Long queryId = tools.checkMessages("a2a-rpc-stream-1", 0L, 1, null, null, null)
                .messages().get(0).messageId();
        tools.sendMessage("a2a-rpc-stream-1", "agent", "done", "finished", null, taskId, queryId, null, null, null, null, null, null);

        // Stream the already-completed task — should get terminal event immediately
        String body = given()
                .contentType("application/json")
                .accept("text/event-stream")
                .body(jsonRpcRequest("message/send",
                        "{\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"check\"}]"
                                + ",\"contextId\":\"a2a-rpc-stream-1\",\"taskId\":\"" + taskId + "\"}}"))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .extract().body().asString();

        assertThat(body).contains("task_status_update");
        assertThat(body).contains("\"final\":true");
    }
}
