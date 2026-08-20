package io.casehub.qhorus.api;

import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

/**
 * JSON-RPC 2.0 dispatch at POST /a2a — method routing, error codes, content negotiation.
 *
 * <p>Tests the refactored A2AResource with a single JSON-RPC endpoint replacing the
 * REST-style endpoints (POST /a2a/message:send, GET /a2a/tasks/{id}).
 *
 * <p>Refs #396.
 */
@QuarkusTest
@TestProfile(A2AEnabledProfile.class)
class A2AJsonRpcDispatchTest {

    @Inject
    QhorusMcpTools tools;

    private static final String A2A_PATH = "/a2a";

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String jsonRpcRequest(String method, String paramsJson) {
        return jsonRpcRequest(method, paramsJson, UUID.randomUUID().toString());
    }

    private String jsonRpcRequest(String method, String paramsJson, String id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
    }

    private String messageSendParams(String contextId, String text, String taskId) {
        String taskPart = taskId != null ? ",\"taskId\":\"" + taskId + "\"" : "";
        return "{\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]"
                + ",\"contextId\":\"" + contextId + "\"" + taskPart + "}}";
    }

    private String tasksGetParams(String taskId) {
        return "{\"id\":\"" + taskId + "\"}";
    }

    private String tasksCancelParams(String taskId) {
        return "{\"id\":\"" + taskId + "\"}";
    }

    // -----------------------------------------------------------------------
    // Method routing — message/send
    // -----------------------------------------------------------------------

    @Test
    void messageSend_returnsJsonRpcResponseWithTask() {
        tools.createChannel("a2a-rpc-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        String reqId = UUID.randomUUID().toString();
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send", messageSendParams("a2a-rpc-1", "hello", null), reqId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("jsonrpc", equalTo("2.0"))
                .body("id", equalTo(reqId))
                .body("result.id", not(emptyOrNullString()))
                .body("result.status.state", equalTo("submitted"));
    }

    @Test
    void messageSend_createsMessageInChannel() {
        tools.createChannel("a2a-rpc-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send", messageSendParams("a2a-rpc-2", "json-rpc message", taskId)))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.id", equalTo(taskId));

        QhorusMcpTools.CheckResult check = tools.checkMessages("a2a-rpc-2", 0L, 10, null, null, null);
        org.junit.jupiter.api.Assertions.assertEquals(1, check.messages().size());
        org.junit.jupiter.api.Assertions.assertEquals("json-rpc message", check.messages().get(0).content());
    }

    // -----------------------------------------------------------------------
    // Method routing — tasks/get
    // -----------------------------------------------------------------------

    @Test
    void tasksGet_returnsTaskWithHistory() {
        tools.createChannel("a2a-rpc-3", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // Create a task via message/send
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send", messageSendParams("a2a-rpc-3", "get-test", taskId)))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200);

        // Retrieve via tasks/get
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("tasks/get", tasksGetParams(taskId)))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("jsonrpc", equalTo("2.0"))
                .body("result.id", equalTo(taskId))
                .body("result.status.state", equalTo("submitted"))
                .body("result.history", hasSize(1));
    }

    @Test
    void tasksGet_unknownTaskId_returnsError() {
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("tasks/get", tasksGetParams(UUID.randomUUID().toString())))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32602))
                .body("error.data", containsString("not found"));}

    // -----------------------------------------------------------------------
    // Method routing — tasks/cancel (NEW)
    // -----------------------------------------------------------------------

    @Test
    void tasksCancel_declinesCommitmentAndReturnsCanceled() {
        tools.createChannel("a2a-rpc-cancel-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // Create a task (opens commitment via QUERY)
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send", messageSendParams("a2a-rpc-cancel-1", "cancel me", taskId)))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200);

        // Cancel the task
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("tasks/cancel", tasksCancelParams(taskId)))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("jsonrpc", equalTo("2.0"))
                .body("result.id", equalTo(taskId))
                .body("result.status.state", equalTo("canceled"));
    }

    @Test
    void tasksCancel_unknownTask_returnsError() {
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("tasks/cancel", tasksCancelParams(UUID.randomUUID().toString())))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32602))
                .body("error.data", containsString("not found"));}

    // -----------------------------------------------------------------------
    // Unknown method — -32601
    // -----------------------------------------------------------------------

    @Test
    void unknownMethod_returnsMethodNotFound() {
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("foo/bar", "{}"))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("jsonrpc", equalTo("2.0"))
                .body("error.code", equalTo(-32601))
                .body("error.message", containsString("not found"));
    }

    // -----------------------------------------------------------------------
    // Invalid params — -32602
    // -----------------------------------------------------------------------

    @Test
    void messageSend_missingMessage_returnsInvalidParams() {
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send", "{}"))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32602));
    }

    @Test
    void messageSend_missingContextId_returnsInvalidParams() {
        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send",
                        "{\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"hi\"}]}}"))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32602));
    }

    // -----------------------------------------------------------------------
    // A2A disabled — 501
    // -----------------------------------------------------------------------
    // (tested in A2AResourceDisabledTest which will be updated)

    // -----------------------------------------------------------------------
    // JSON-RPC response wrapping — id is echoed
    // -----------------------------------------------------------------------

    @Test
    void responseEchoesRequestId() {
        tools.createChannel("a2a-rpc-echo", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String reqId = "my-custom-id-123";

        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("message/send", messageSendParams("a2a-rpc-echo", "echo test", null), reqId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("id", equalTo(reqId));
    }

    @Test
    void errorResponseEchoesRequestId() {
        String reqId = "error-id-456";

        given()
                .contentType("application/json")
                .accept("application/json")
                .body(jsonRpcRequest("unknown/method", "{}", reqId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("id", equalTo(reqId));
    }
}
