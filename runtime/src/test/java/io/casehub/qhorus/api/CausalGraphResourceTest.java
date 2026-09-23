package io.casehub.qhorus.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CausalGraphResourceTest {

    @Test
    void getGraph_unknownCorrelation_returnsEmptyGraph() {
        given().when().get("/api/causal-graph/" + UUID.randomUUID())
            .then()
            .statusCode(200)
            .body("nodes.size()", is(0))
            .body("edges.size()", is(0))
            .body("outcome", equalTo("OPEN"))
            .body("rootEntryId", nullValue())
            .body("truncated", is(false));
    }

    @Test
    void getAttribution_unknownEntry_returnsEmptyArray() {
        given().when().get("/api/causal-graph/attribution/" + UUID.randomUUID())
            .then()
            .statusCode(200)
            .body("$.size()", is(0));
    }

    @Test
    void getAttribution_invalidUuid_returns400() {
        given().when().get("/api/causal-graph/attribution/not-a-uuid")
            .then()
            .statusCode(400)
            .body("error", containsString("Invalid entry ID"));
    }

    @Test
    void getGraph_withLimitParam_respected() {
        given().queryParam("limit", 10)
            .when().get("/api/causal-graph/" + UUID.randomUUID())
            .then()
            .statusCode(200)
            .body("outcome", equalTo("OPEN"));
    }
}
