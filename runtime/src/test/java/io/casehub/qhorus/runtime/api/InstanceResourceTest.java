package io.casehub.qhorus.runtime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class InstanceResourceTest {

    @Test
    void registerAndGetInstance() {
        String body = """
                {"instanceId": "rest-agent-1", "description": "Test agent",
                 "capabilities": ["summarise", "translate"]}
                """;

        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/instances")
                .then().statusCode(201)
                .body("instanceId", equalTo("rest-agent-1"))
                .body("description", equalTo("Test agent"))
                .body("capabilities", hasItems("summarise", "translate"));

        given().when().get("/api/instances/rest-agent-1")
                .then().statusCode(200)
                .body("instanceId", equalTo("rest-agent-1"));
    }

    @Test
    void listInstancesWithCapabilityFilter() {
        String agent1 = """
                {"instanceId": "filter-rest-1", "description": "Agent 1",
                 "capabilities": ["summarise"]}
                """;
        String agent2 = """
                {"instanceId": "filter-rest-2", "description": "Agent 2",
                 "capabilities": ["translate"]}
                """;

        given().contentType(ContentType.JSON).body(agent1)
                .when().post("/api/instances").then().statusCode(201);
        given().contentType(ContentType.JSON).body(agent2)
                .when().post("/api/instances").then().statusCode(201);

        given().queryParam("capability", "summarise")
                .when().get("/api/instances")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("instanceId", hasItem("filter-rest-1"));
    }

    @Test
    void deregisterInstance() {
        String body = """
                {"instanceId": "deregister-rest-1", "description": "To be removed",
                 "capabilities": []}
                """;
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/instances").then().statusCode(201);

        given().when().delete("/api/instances/deregister-rest-1")
                .then().statusCode(204);

        given().when().get("/api/instances/deregister-rest-1")
                .then().statusCode(404);
    }

    @Test
    void getNonExistentInstanceReturns404() {
        given().when().get("/api/instances/does-not-exist")
                .then().statusCode(404);
    }

    @Test
    void deregisterNonExistentInstanceReturns404() {
        given().when().delete("/api/instances/never-registered")
                .then().statusCode(404);
    }

    @Test
    void registerWithMetadata() {
        String body = """
                {"instanceId": "meta-rest-1", "description": "Agent with metadata",
                 "capabilities": ["analyse"],
                 "metadata": {"model": "opus-4.6", "region": "us-east"}}
                """;

        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/instances")
                .then().statusCode(201)
                .body("instanceId", equalTo("meta-rest-1"))
                .body("metadata.model", equalTo("opus-4.6"))
                .body("metadata.region", equalTo("us-east"));
    }
}
