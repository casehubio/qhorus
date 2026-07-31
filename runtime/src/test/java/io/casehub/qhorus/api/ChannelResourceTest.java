package io.casehub.qhorus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ChannelResourceTest {

    @Test
    void createChannelReturns201() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "rest-create-test", "description": "A test channel"}
                        """)
                .when().post("/api/channels")
                .then()
                .statusCode(201)
                .body("name", equalTo("rest-create-test"))
                .body("description", equalTo("A test channel"))
                .body("semantic", equalTo("APPEND"))
                .body("channelId", notNullValue())
                .body("paused", equalTo(false));
    }

    @Test
    void createChannelWithInvalidSlugReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "INVALID SLUG!"}
                        """)
                .when().post("/api/channels")
                .then()
                .statusCode(400)
                .body("error", notNullValue());
    }

    @Test
    void listChannelsReturnsCreatedChannels() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name": "rest-list-a"}
                        """)
                .when().post("/api/channels").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("""
                        {"name": "rest-list-b"}
                        """)
                .when().post("/api/channels").then().statusCode(201);

        given()
                .when().get("/api/channels")
                .then()
                .statusCode(200)
                .body("findAll { it.name == 'rest-list-a' }.size()", equalTo(1))
                .body("findAll { it.name == 'rest-list-b' }.size()", equalTo(1));
    }

    @Test
    void listChannelsWithPrefixFilter() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name": "rest-prefix-alpha"}
                        """)
                .when().post("/api/channels").then().statusCode(201);

        given()
                .queryParam("prefix", "rest-prefix-")
                .when().get("/api/channels")
                .then()
                .statusCode(200)
                .body("findAll { it.name.startsWith('rest-prefix-') }.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void getChannelByUuid() {
        final String channelId = given().contentType(ContentType.JSON)
                .body("""
                        {"name": "rest-get-uuid"}
                        """)
                .when().post("/api/channels")
                .then().statusCode(201)
                .extract().path("channelId");

        given()
                .when().get("/api/channels/{id}", channelId)
                .then()
                .statusCode(200)
                .body("channelId", equalTo(channelId))
                .body("name", equalTo("rest-get-uuid"));
    }

    @Test
    void getChannelBySlug() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name": "rest-get-slug"}
                        """)
                .when().post("/api/channels").then().statusCode(201);

        given()
                .when().get("/api/channels/{id}", "rest-get-slug")
                .then()
                .statusCode(200)
                .body("name", equalTo("rest-get-slug"));
    }

    @Test
    void getChannelNotFoundReturns404() {
        given()
                .when().get("/api/channels/{id}", "nonexistent-channel-xyz")
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    @Test
    void deleteChannelReturns204() {
        final String channelId = given().contentType(ContentType.JSON)
                .body("""
                        {"name": "rest-delete-me"}
                        """)
                .when().post("/api/channels")
                .then().statusCode(201)
                .extract().path("channelId");

        given()
                .queryParam("force", true)
                .when().delete("/api/channels/{id}", channelId)
                .then()
                .statusCode(204);

        given()
                .when().get("/api/channels/{id}", channelId)
                .then()
                .statusCode(404);
    }

    @Test
    void pauseAndResumeChannel() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-pause-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201)
                                        .body("paused", equalTo(false))
                                        .extract().path("channelId");

        given()
                .when().post("/api/channels/{id}/pause", channelId)
                .then()
                .statusCode(200)
                .body("paused", equalTo(true));

        given()
                .when().post("/api/channels/{id}/resume", channelId)
                .then()
                .statusCode(200)
                .body("paused", equalTo(false));
    }

    @Test
    void setAllowedWriters() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-writers-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"values": ["agent-a", "agent-b"]}
                     """)
               .when().put("/api/channels/{id}/allowed-writers", channelId)
               .then()
               .statusCode(200)
               .body("allowedWriters", hasItems("agent-a", "agent-b"));
    }

    @Test
    void setAdminInstances() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-admins-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"values": ["admin-1"]}
                     """)
               .when().put("/api/channels/{id}/admin-instances", channelId)
               .then()
               .statusCode(200)
               .body("adminInstances", hasItem("admin-1"));
    }

    @Test
    void setReviewerInstances() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-reviewers-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"values": ["reviewer-1"]}
                     """)
               .when().put("/api/channels/{id}/reviewer-instances", channelId)
               .then()
               .statusCode(200)
               .body("reviewerInstances", hasItem("reviewer-1"));
    }

    @Test
    void setTypeConstraints() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-types-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"allowedTypes": ["QUERY", "RESPONSE"]}
                     """)
               .when().put("/api/channels/{id}/type-constraints", channelId)
               .then()
               .statusCode(200)
               .body("allowedTypes", hasItems("QUERY", "RESPONSE"));
    }

    @Test
    void setRateLimits() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-rates-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"perChannel": 50, "perInstance": 5}
                     """)
               .when().put("/api/channels/{id}/rate-limits", channelId)
               .then()
               .statusCode(200)
               .body("rateLimitPerChannel", equalTo(50))
               .body("rateLimitPerInstance", equalTo(5));
    }

    @Test
    void setProtocols() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-protocols-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"values": ["REQUEST_RESPONSE"]}
                     """)
               .when().put("/api/channels/{id}/protocols", channelId)
               .then()
               .statusCode(200)
               .body("protocols", hasItem("REQUEST_RESPONSE"));
    }

    @Test
    void setProtocolParticipants() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-participants-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"values": ["agent-a", "agent-b"]}
                     """)
               .when().put("/api/channels/{id}/protocol-participants", channelId)
               .then()
               .statusCode(200)
               .body("protocolParticipants", hasItems("agent-a", "agent-b"));
    }

    @Test
    void setDeliveryTracking() {
        final String channelId = given().contentType(ContentType.JSON)
                                        .body("""
                                              {"name": "rest-tracking-test"}
                                              """)
                                        .when().post("/api/channels")
                                        .then().statusCode(201).extract().path("channelId");

        given().contentType(ContentType.JSON)
               .body("""
                     {"enabled": true}
                     """)
               .when().put("/api/channels/{id}/delivery-tracking", channelId)
               .then()
               .statusCode(200)
               .body("trackDelivery", equalTo(true));
    }

    @Test
    void settingsEndpointReturns404ForUnknownChannel() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"values": ["x"]}
                     """)
               .when().put("/api/channels/{id}/allowed-writers", "no-such-channel")
               .then()
               .statusCode(404);
    }


}
