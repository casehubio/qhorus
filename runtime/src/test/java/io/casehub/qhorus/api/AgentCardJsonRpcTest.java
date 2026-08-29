package io.casehub.qhorus.api;

import io.casehub.qhorus.runtime.instance.InstanceService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Agent Card — directory card at /.well-known/agent.json with authentication and agents[] fields.
 *
 * <p>Tests the updated AgentCardResource that uses Layer 0 AgentCard types and serves
 * the directory card at the A2A-compliant path (was agent-card.json).
 *
 * <p>Refs #396.
 */
@QuarkusTest
@TestProfile(A2AEnabledProfile.class)
class AgentCardJsonRpcTest {

    @Inject
    InstanceService instanceService;

    // -----------------------------------------------------------------------
    // Directory card at /.well-known/agent.json
    // -----------------------------------------------------------------------

    @Test
    void directoryCard_atNewPath_returnsCard() {
        given()
                .accept("application/json")
                .when().get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .body("name", not(emptyOrNullString()))
                .body("version", not(emptyOrNullString()));
    }

    @Test
    void directoryCard_hasAuthenticationField() {
        given()
                .accept("application/json")
                .when().get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .body("authentication", notNullValue())
                .body("authentication.schemes", notNullValue());
    }

    @Test
    void directoryCard_hasCapabilitiesWithPushNotifications() {
        given()
                .accept("application/json")
                .when().get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .body("capabilities.streaming", equalTo(true))
                .body("capabilities.pushNotifications", equalTo(true));
    }

    @Test
    void directoryCard_hasAgentsArray() {
        given()
                .accept("application/json")
                .when().get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .body("agents", notNullValue());
    }

    @Test
    void directoryCard_hasTenancyId() {
        given()
                .accept("application/json")
                .when().get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .body("tenancyId", not(emptyOrNullString()));
    }

    // -----------------------------------------------------------------------
    // Per-agent cards at /.well-known/agents/{instanceId}.json
    // -----------------------------------------------------------------------

    @Test
    void perAgentCard_registeredInstance_returnsCard() {
        instanceService.register("card-test-agent", "Test agent for card", java.util.List.of());

        given()
                .accept("application/json")
                .when().get("/.well-known/agents/card-test-agent.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("card-test-agent"));}

    @Test
    void perAgentCard_unknownInstance_returns404() {
        given()
                .accept("application/json")
                .when().get("/.well-known/agents/nonexistent-agent.json")
                .then()
                .statusCode(404);
    }

    // -----------------------------------------------------------------------
    // Directory card agents[] lists registered instances
    // -----------------------------------------------------------------------

    @Test
    void directoryCard_agentsArray_includesRegisteredInstances() {
        instanceService.register("card-list-agent", "Agent for listing", java.util.List.of());

        given()
                .accept("application/json")
                .when().get("/.well-known/agent.json")
                .then()
                .statusCode(200)
                .body("agents.name", hasItem("card-list-agent"));}
}
