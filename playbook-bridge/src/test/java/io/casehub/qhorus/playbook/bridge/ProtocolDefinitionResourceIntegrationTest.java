package io.casehub.qhorus.playbook.bridge;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ProtocolDefinitionResourceIntegrationTest {

    @Inject ProtocolDefinitionStore store;
    @InjectMock CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        when(currentPrincipal.tenancyId()).thenReturn("DEFAULT");
    }

    @Test
    void put_returns200_withJsonBody() {
        UUID channelId = UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"version: 1.0\\nsteps:\\n  - check: open_queries\\n\"}")
                .when()
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "approval-gate")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("protocolName", equalTo("approval-gate"))
                .body("channelId", equalTo(channelId.toString()))
                .body("yamlContent", equalTo("version: 1.0\nsteps:\n  - check: open_queries\n"))
                .body("id", notNullValue())
                .body("tenancyId", equalTo("DEFAULT"));
    }

    @Test
    void put_returns400_forBlankYaml() {
        UUID channelId = UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"\"}")
                .when()
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "gate")
                .then()
                .statusCode(400)
                .body("error", equalTo("yamlContent is required"));
    }

    @Test
    void put_returns400_forNullYaml() {
        UUID channelId = UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "gate")
                .then()
                .statusCode(400);
    }

    @Test
    void put_returns400_forInvalidDeadlineDuration() {
        UUID channelId = UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"deadlines:\\n  timeout: not-a-duration\\n\"}")
                .when()
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "gate")
                .then()
                .statusCode(400)
                .body("error", equalTo("Invalid deadline duration: not-a-duration"));
    }

    @Test
    void put_accepts_validDeadlineDuration() {
        UUID channelId = UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"deadlines:\\n  timeout: PT5M\\n\"}")
                .when()
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "deadline-gate")
                .then()
                .statusCode(200)
                .body("protocolName", equalTo("deadline-gate"));
    }

    @Test
    void get_returns200_forExisting() {
        UUID channelId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"version: 1.0\\n\"}")
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "get-gate");

        given()
                .when()
                .get("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "get-gate")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("protocolName", equalTo("get-gate"))
                .body("yamlContent", equalTo("version: 1.0\n"));
    }

    @Test
    void get_returns404_forMissing() {
        given()
                .when()
                .get("/api/channels/{channelId}/protocol-definitions/{name}", UUID.randomUUID(), "nonexistent")
                .then()
                .statusCode(404);
    }

    @Test
    void delete_returns204() {
        UUID channelId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"version: 1.0\\n\"}")
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "del-gate");

        given()
                .when()
                .delete("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "del-gate")
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "del-gate")
                .then()
                .statusCode(404);
    }

    @Test
    void list_returnsJsonArray() {
        UUID channelId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"a\\n\"}")
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "list-gate-a");
        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"b\\n\"}")
                .put("/api/channels/{channelId}/protocol-definitions/{name}", channelId, "list-gate-b");

        given()
                .when()
                .get("/api/channels/{channelId}/protocol-definitions", channelId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(2));
    }

    @Test
    void put_uuidPathParamBinding() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"yamlContent\":\"version: 1.0\\n\"}")
                .when()
                .put("/api/channels/not-a-uuid/protocol-definitions/gate")
                .then()
                .statusCode(404);
    }
}
