package io.casehub.qhorus.api;

import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Issue #35 — GET /a2a/tasks/{id} returns A2A Task status via correlation_id lookup.
 *
 * <p>
 * State derivation rules:
 * <ul>
 * <li>No messages → 404 Not Found</li>
 * <li>Only QUERY/COMMAND messages → {@code "submitted"}</li>
 * <li>Any STATUS messages present → {@code "working"}</li>
 * <li>Any RESPONSE or DONE message → {@code "completed"}</li>
 * <li>Any FAILURE or DECLINE message → {@code "failed"}</li>
 * </ul>
 *
 * <p>
 * Three test levels:
 * <ul>
 * <li>Unit: each state transition, 404, history mapping</li>
 * <li>Integration: task created via send endpoint, retrieved via get</li>
 * <li>End-to-end: full A2A lifecycle — send → check submitted → respond → check completed</li>
 * </ul>
 *
 * <p>
 * Note: HTTP tests avoid {@code @TestTransaction} — see A2ASendMessageTest for explanation.
 *
 * <p>
 * Refs #35, Epic #32.
 */
@QuarkusTest
@TestProfile(A2AEnabledProfile.class)
class A2AGetTaskTest {

    @Inject
    QhorusMcpTools tools;

    private static final String A2A_PATH = "/a2a";

    /** Get the messageId of the first message in a channel (used for inReplyTo). */
    private Long firstMessageId(String channel) {
        QhorusMcpTools.CheckResult check = tools.checkMessages(channel, 0L, 1, null, null, null);
        if (check.messages().isEmpty()) return null;
        return check.messages().get(0).messageId();
    }

    // -----------------------------------------------------------------------
    // Helper — send an A2A message and return the task id
    // -----------------------------------------------------------------------

    private String sendA2A(String channel, String role, String text, String taskId) {
        String taskPart = taskId != null ? ",\"taskId\":\"" + taskId + "\"" : "";
        String reqId    = java.util.UUID.randomUUID().toString();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"" + reqId + "\",\"method\":\"message/send\",\"params\":"
                      + "{\"message\":{\"role\":\"" + role + "\","
                      + "\"contextId\":\"" + channel + "\","
                      + "\"parts\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]"
                      + taskPart + "}}}";

        return given()
                       .contentType("application/json")
                       .accept("application/json")
                       .body(body)
                       .when().post(A2A_PATH)
                       .then()
                       .statusCode(200)
                       .extract().path("result.id");
    }

    private String getTaskBody(String taskId) {
        String reqId = java.util.UUID.randomUUID().toString();
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + reqId + "\",\"method\":\"tasks/get\",\"params\":{\"id\":\"" + taskId + "\"}}";
    }


    // -----------------------------------------------------------------------
    // Unit — state derivation and 404
    // -----------------------------------------------------------------------

    @Test
    void unknownTaskIdReturns404() {
        String unknownId = UUID.randomUUID().toString();
        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(unknownId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("error.code", equalTo(-32602));
    }

    @Test
    void taskWithOnlyRequestMessageIsSubmitted() {
        tools.createChannel("a2a-gt-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();
        sendA2A("a2a-gt-1", "user", "initial request", taskId);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("submitted"))
                .body("result.id", equalTo(taskId))
                .body("result.contextId", equalTo("a2a-gt-1"));
    }

    @Test
    void taskWithStatusMessageIsWorking() {
        tools.createChannel("a2a-gt-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // Request message (submitted)
        sendA2A("a2a-gt-2", "user", "initial request", taskId);

        // Agent sends a status update — transitions to working
        tools.sendMessage("a2a-gt-2", "agent", "status", "processing...", null, taskId, null, null, null, null, null, null, null);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("working"));
    }

    @Test
    void taskWithResponseMessageIsCompleted() {
        tools.createChannel("a2a-gt-3", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        sendA2A("a2a-gt-3", "user", "request", taskId);
        Long queryId = firstMessageId("a2a-gt-3");
        tools.sendMessage("a2a-gt-3", "agent", "response", "here is the answer", null, taskId, queryId, null, null, null, null, null, null);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("completed"));
    }

    @Test
    void taskWithDoneMessageIsCompleted() {
        tools.createChannel("a2a-gt-4", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        sendA2A("a2a-gt-4", "user", "request", taskId);
        Long queryId = firstMessageId("a2a-gt-4");
        tools.sendMessage("a2a-gt-4", "agent", "done", "task finished", null, taskId, queryId, null, null, null, null, null, null);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("completed"));
    }

    @Test
    void taskWithFailureMessageIsFailed() {
        tools.createChannel("a2a-gt-4b", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        sendA2A("a2a-gt-4b", "user", "request", taskId);
        Long queryId = firstMessageId("a2a-gt-4b");
        tools.sendMessage("a2a-gt-4b", "agent", "failure", "could not complete the requested action", null, taskId, queryId, null, null, null, null, null, null);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("failed"));
    }

    @Test
    void taskIdAndContextIdPresentInResponse() {
        tools.createChannel("a2a-gt-5", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();
        sendA2A("a2a-gt-5", "user", "hello", taskId);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.id", equalTo(taskId))
                .body("result.contextId", equalTo("a2a-gt-5"));
    }

    // -----------------------------------------------------------------------
    // History — messages in task
    // -----------------------------------------------------------------------

    @Test
    void historyContainsSentMessage() {
        tools.createChannel("a2a-gt-6", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();
        sendA2A("a2a-gt-6", "user", "the content", taskId);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.history", hasSize(1))
                .body("result.history[0].parts[0].text", equalTo("the content"))
                .body("result.history[0].role", equalTo("human:user"));
    }

    @Test
    void historyContainsAllMessagesInOrder() {
        tools.createChannel("a2a-gt-7", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        sendA2A("a2a-gt-7", "user", "request message", taskId);
        Long queryId = firstMessageId("a2a-gt-7");
        tools.sendMessage("a2a-gt-7", "agent", "status", "processing", null, taskId, null, null, null, null, null, null, null);
        tools.sendMessage("a2a-gt-7", "agent", "response", "final answer", null, taskId, queryId, null, null, null, null, null, null);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.history", hasSize(3))
                .body("result.history[0].role", equalTo("human:user"))
                .body("result.history[1].role", equalTo("agent"))
                .body("result.history[2].parts[0].text", equalTo("final answer"));
    }

    // -----------------------------------------------------------------------
    // Integration — task created via A2A send then retrieved
    // -----------------------------------------------------------------------

    @Test
    void taskCreatedViaSendIsRetrievableViaGet() {
        tools.createChannel("a2a-gt-8", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // Create via POST
        String returnedId = sendA2A("a2a-gt-8", "orchestrator", "do the work", taskId);
        assertEquals(taskId, returnedId);

        // Retrieve via GET
        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.id", equalTo(taskId))
                .body("result.status.state", equalTo("submitted"))
                .body("result.contextId", equalTo("a2a-gt-8"))
                .body("result.history", hasSize(1));
    }

    // -----------------------------------------------------------------------
    // CommitmentStore-based state — durable task state via commitment lifecycle
    // -----------------------------------------------------------------------

    @Test
    void taskWithDoneViaCommitment_stateIsCompleted() {
        tools.createChannel("a2a-gt-commit-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // Send QUERY via A2A (creates commitment OPEN)
        sendA2A("a2a-gt-commit-1", "user", "please do this", taskId);
        Long queryId = firstMessageId("a2a-gt-commit-1");

        // Resolve via DONE (transitions commitment FULFILLED)
        tools.sendMessage("a2a-gt-commit-1", "agent", "done", "all finished", null, taskId, queryId, null, null, null, null, null, null);

        // getTask() should return completed state via CommitmentStore
        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("completed"))
                .body("result.history", hasSize(2));  // QUERY + DONE messages
    }

    @Test
    void taskWithDelegatedState_isWorking() {
        tools.createChannel("a2a-gt-del-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // QUERY via A2A creates an OPEN commitment
        sendA2A("a2a-gt-del-1", "user", "please delegate this", taskId);
        Long queryId = firstMessageId("a2a-gt-del-1");

        // Agent sends HANDOFF — parent commitment becomes DELEGATED (terminal),
        // child OPEN commitment created for delegate. findByCorrelationId returns
        // child OPEN → OPEN-guard routes to fromMessageHistory → HANDOFF → "working".
        tools.sendMessage("a2a-gt-del-1", "agent", "handoff", "delegating to specialist", null, taskId,
                queryId, null, "role:specialist", null, null, null, null);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("working"));
    }

    @Test
    void taskWithHandoffMessageIsWorking() {
        tools.createChannel("a2a-gt-del-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // Send a COMMAND first so we have a messageId for inReplyTo.
        var cmd = tools.sendMessage("a2a-gt-del-2", "agent", "command", "originating task", null, taskId, null, null, null, null, null, null, null);
        // HANDOFF means the obligation is being transferred — state is "working".
        tools.sendMessage("a2a-gt-del-2", "agent", "handoff", "delegated", null, taskId,
                cmd.messageId(), null, "role:specialist", null, null, null, null);

        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("working"));
    }

    // -----------------------------------------------------------------------
    // End-to-end — full A2A lifecycle
    // -----------------------------------------------------------------------

    @Test
    void e2eFullA2ALifecycleSubmittedWorkingCompleted() {
        tools.createChannel("a2a-e2e-gt-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String taskId = UUID.randomUUID().toString();

        // 1. External orchestrator sends task via A2A
        sendA2A("a2a-e2e-gt-1", "orchestrator", "analyse this data", taskId);

        // 2. Orchestrator polls — task is submitted
        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("submitted"));

        // 3. Internal agent (MCP) picks up task, sends status update
        tools.sendMessage("a2a-e2e-gt-1", "analyst-agent", "status", "I'm working on it", null, taskId, null, null, null, null, null, null, null);

        // 4. Orchestrator polls again — task is working
        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("working"));

        // 5. Agent completes task
        Long queryId = firstMessageId("a2a-e2e-gt-1");
        tools.sendMessage("a2a-e2e-gt-1", "analyst-agent", "response", "Analysis complete: 42", null, taskId, queryId, null, null, null, null, null, null);

        // 6. Orchestrator polls — task is completed with full history
        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(taskId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.status.state", equalTo("completed"))
                .body("result.history", hasSize(3))
                .body("result.history[2].parts[0].text", equalTo("Analysis complete: 42"));
    }

    @Test
    void e2eAutoGeneratedTaskIdRoundtrip() {
        tools.createChannel("a2a-e2e-gt-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // 1. Send without explicit taskId
        String generatedId = sendA2A("a2a-e2e-gt-2", "user", "work without explicit id", null);
        assertNotNull(generatedId);
        assertDoesNotThrow(() -> UUID.fromString(generatedId));

        // 2. Retrieve using the auto-generated id
        given()
                .contentType("application/json").accept("application/json")
                .body(getTaskBody(generatedId))
                .when().post(A2A_PATH)
                .then()
                .statusCode(200)
                .body("result.id", equalTo(generatedId))
                .body("result.status.state", equalTo("submitted"));
    }
}
