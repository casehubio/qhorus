package io.casehub.qhorus.api;

import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(A2AEnabledProfile.class)
class A2ATenantScopingTest {

    private String channel;

    @Inject
    QhorusMcpTools tools;

    @BeforeEach
    void ensureChannel() {
        channel = "a2a-ts-" + UUID.randomUUID().toString().substring(0, 8);
        final String ch = channel;
        QuarkusTransaction.requiringNew().run(() ->
                                                      tools.createChannel(ch, "A2A tenant scoping test channel", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void sendMessage_withoutTenancyHeader_usesDefaultTenantAndSucceeds() {
        final String taskId = UUID.randomUUID().toString();
        given()
                .contentType("application/json").accept("application/json")
                .body(sendBody(channel, taskId))
                .when().post("/a2a")
                .then().statusCode(200)
                .body("result.status.state", equalTo("submitted"));
    }

    @Test
    void sendMessage_withNonExistentTenantHeader_returnsChannelNotFoundError() {
        given()
                .contentType("application/json").accept("application/json")
                .header("X-Tenancy-ID", "non-existent-tenant")
                .body(sendBody(channel, UUID.randomUUID().toString()))
                .when().post("/a2a")
                .then().statusCode(200)
                .body("error.code", equalTo(-32602));
    }

    @Test
    void getTask_withMatchingTenantContext_returnsTask() {
        final String taskId = UUID.randomUUID().toString();
        given()
                .contentType("application/json").accept("application/json")
                .body(sendBody(channel, taskId))
                .when().post("/a2a")
                .then().statusCode(200);

        given()
                .contentType("application/json").accept("application/json")
                .body(getBody(taskId))
                .when().post("/a2a")
                .then().statusCode(200)
                .body("result.id", equalTo(taskId));
    }

    @Test
    void getTask_withDifferentTenantHeader_returnsNotFoundError() {
        final String taskId = UUID.randomUUID().toString();
        given()
                .contentType("application/json").accept("application/json")
                .body(sendBody(channel, taskId))
                .when().post("/a2a")
                .then().statusCode(200);

        given()
                .contentType("application/json").accept("application/json")
                .header("X-Tenancy-ID", "different-tenant")
                .body(getBody(taskId))
                .when().post("/a2a")
                .then().statusCode(200)
                .body("error.code", equalTo(-32602));
    }

    @Test
    void getTask_commitmentLevelTenantIsolation_crossTenantTaskNotVisible() {
        final String taskA = UUID.randomUUID().toString();
        final String taskB = UUID.randomUUID().toString();

        given().contentType("application/json").accept("application/json")
               .body(sendBody(channel, taskA))
               .when().post("/a2a").then().statusCode(200);

        given().contentType("application/json").accept("application/json")
               .body(sendBody(channel, taskB))
               .when().post("/a2a").then().statusCode(200);

        given().contentType("application/json").accept("application/json")
               .body(getBody(taskA)).when().post("/a2a")
               .then().statusCode(200).body("result.id", equalTo(taskA));
        given().contentType("application/json").accept("application/json")
               .body(getBody(taskB)).when().post("/a2a")
               .then().statusCode(200).body("result.id", equalTo(taskB));

        given().contentType("application/json").accept("application/json")
               .header("X-Tenancy-ID", "tenant-other-267")
               .body(getBody(taskA)).when().post("/a2a")
               .then().statusCode(200).body("error.code", equalTo(-32602));
        given().contentType("application/json").accept("application/json")
               .header("X-Tenancy-ID", "tenant-other-267")
               .body(getBody(taskB)).when().post("/a2a")
               .then().statusCode(200).body("error.code", equalTo(-32602));
    }

    private static String sendBody(String channel, String taskId) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + UUID.randomUUID() + "\",\"method\":\"message/send\",\"params\":"
               + "{\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"Hello from test\"}],"
               + "\"contextId\":\"" + channel + "\",\"taskId\":\"" + taskId + "\"}}}";
    }

    private static String getBody(String taskId) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + UUID.randomUUID() + "\",\"method\":\"tasks/get\",\"params\":{\"id\":\"" + taskId + "\"}}";
    }
}
