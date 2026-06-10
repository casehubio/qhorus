package io.casehub.qhorus.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that {@code GET /.well-known/agent-card.json} reflects the tenant
 * from the {@code X-Tenancy-ID} header (set by {@link
 * io.casehub.qhorus.runtime.identity.TenancyContextFilter}).
 *
 * <p>Refs #264, #265.
 */
@QuarkusTest
class AgentCardTenantTest {

    @Test
    void agentCard_withTenancyHeader_includesTenancyIdField() {
        given()
                .header("X-Tenancy-ID", "tenant-alpha")
                .when().get("/.well-known/agent-card.json")
                .then().statusCode(200)
                .body("tenancyId", equalTo("tenant-alpha"));
    }

    @Test
    void agentCard_withoutTenancyHeader_includesDefaultTenancyId() {
        given()
                .when().get("/.well-known/agent-card.json")
                .then().statusCode(200)
                .body("tenancyId", equalTo(TenancyConstants.DEFAULT_TENANT_ID));
    }

    @Test
    void agentCard_hasRequiredA2AFields() {
        given()
                .when().get("/.well-known/agent-card.json")
                .then().statusCode(200)
                .body("name", notNullValue())
                .body("description", notNullValue())
                .body("version", notNullValue())
                .body("skills", notNullValue());
    }
}
