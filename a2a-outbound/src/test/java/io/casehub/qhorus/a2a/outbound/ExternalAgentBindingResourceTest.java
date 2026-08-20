package io.casehub.qhorus.a2a.outbound;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.persistence.memory.InMemoryExternalAgentBindingStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ExternalAgentBindingResourceTest {

    @Inject InMemoryExternalAgentBindingStore bindingStore;

    @InjectMock CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        Mockito.when(currentPrincipal.tenancyId()).thenReturn(TenancyConstants.DEFAULT_TENANT_ID);
        bindingStore.findAll().forEach(b -> bindingStore.deleteByInstanceId(b.instanceId()));
    }

    @Test
    void put_createsBinding() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"endpoint": "https://agent.example.com/a2a", "authConfigKey": "my-key", "protocolVersion": "1.0"}
                """)
        .when()
            .put("/a2a-outbound/bindings/ext-agent-1")
        .then()
            .statusCode(200)
            .body("instanceId", equalTo("ext-agent-1"))
            .body("endpoint", equalTo("https://agent.example.com/a2a"))
            .body("authConfigKey", equalTo("my-key"))
            .body("protocolVersion", equalTo("1.0"))
            .body("id", notNullValue());

        assertThat(bindingStore.findByInstanceId("ext-agent-1")).isPresent();
    }

    @Test
    void put_updatesExistingBinding() {
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), "ext-update",
                "https://old.com/a2a", null, "1.0", Instant.now()));

        given()
            .contentType(ContentType.JSON)
            .body("""
                {"endpoint": "https://new.com/a2a", "authConfigKey": "new-key", "protocolVersion": "2.0"}
                """)
        .when()
            .put("/a2a-outbound/bindings/ext-update")
        .then()
            .statusCode(200)
            .body("endpoint", equalTo("https://new.com/a2a"))
            .body("authConfigKey", equalTo("new-key"))
            .body("protocolVersion", equalTo("2.0"));
    }

    @Test
    void put_missingEndpoint_returns400() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"authConfigKey": "key"}
                """)
        .when()
            .put("/a2a-outbound/bindings/ext-no-endpoint")
        .then()
            .statusCode(400);
    }

    @Test
    void put_defaultsProtocolVersion() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {"endpoint": "https://agent.example.com/a2a"}
                """)
        .when()
            .put("/a2a-outbound/bindings/ext-default-ver")
        .then()
            .statusCode(200)
            .body("protocolVersion", equalTo("1.0"));
    }

    @Test
    void get_existingBinding_returns200() {
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), "ext-get",
                "https://get.example.com/a2a", "token-key", "1.0", Instant.now()));

        given()
        .when()
            .get("/a2a-outbound/bindings/ext-get")
        .then()
            .statusCode(200)
            .body("instanceId", equalTo("ext-get"))
            .body("endpoint", equalTo("https://get.example.com/a2a"));
    }

    @Test
    void get_nonExistent_returns404() {
        given()
        .when()
            .get("/a2a-outbound/bindings/nonexistent")
        .then()
            .statusCode(404);
    }

    @Test
    void list_returnsAllBindings() {
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), "ext-list-a",
                "https://a.com/a2a", null, "1.0", Instant.now()));
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), "ext-list-b",
                "https://b.com/a2a", null, "1.0", Instant.now()));

        given()
        .when()
            .get("/a2a-outbound/bindings")
        .then()
            .statusCode(200)
            .body("$", hasSize(2));
    }

    @Test
    void delete_removesBinding() {
        bindingStore.put(new ExternalAgentBinding(UUID.randomUUID(), "ext-del",
                "https://del.example.com/a2a", null, "1.0", Instant.now()));

        given()
        .when()
            .delete("/a2a-outbound/bindings/ext-del")
        .then()
            .statusCode(204);

        assertThat(bindingStore.findByInstanceId("ext-del")).isEmpty();
    }

    @Test
    void delete_nonExistent_returns204() {
        given()
        .when()
            .delete("/a2a-outbound/bindings/nonexistent")
        .then()
            .statusCode(204);
    }
}
