package io.casehub.qhorus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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

    // --- Aggregation endpoints ---

    @Test
    void feedReturnsArray() {
        given().when().get("/api/channels/feed")
               .then().statusCode(200)
               .body("$.size()", greaterThanOrEqualTo(0));
    }

    @Test
    void timelineReturnsMessagesForChannel() {
        var channelId = createChannel("timeline-test");
        given().when().get("/api/channels/" + channelId + "/timeline")
               .then().statusCode(200)
               .body("$.size()", greaterThanOrEqualTo(0));
    }

    // --- Reaction CRUD ---

    @Test
    void reactionAddAndList() {
        var channelId = createChannel("react-test");
        var msgId = postMessage(channelId, "react to me");
        given().contentType(ContentType.JSON)
               .body("""
                     {"emoji": "thumbsup"}
                     """)
               .when().post("/api/channels/" + channelId + "/messages/" + msgId + "/reactions")
               .then().statusCode(200);

        given().when().get("/api/channels/" + channelId + "/messages/" + msgId + "/reactions")
               .then().statusCode(200)
               .body("$", hasItem("thumbsup"));
    }

    @Test
    void reactionRemove() {
        var channelId = createChannel("react-rm-test");
        var msgId = postMessage(channelId, "react then remove");
        given().contentType(ContentType.JSON)
               .body("""
                     {"emoji": "thumbsup"}
                     """)
               .when().post("/api/channels/" + channelId + "/messages/" + msgId + "/reactions")
               .then().statusCode(200);

        given().when().delete("/api/channels/" + channelId + "/messages/" + msgId + "/reactions/thumbsup")
               .then().statusCode(200);
    }

    // --- Topic CRUD ---

    @Test
    void topicCreateAndList() {
        var channelId = createChannel("topic-test");
        given().contentType(ContentType.JSON)
               .body("""
                     {"name": "design"}
                     """)
               .when().post("/api/channels/" + channelId + "/topics")
               .then().statusCode(200)
               .body("name", equalTo("design"));

        given().when().get("/api/channels/" + channelId + "/topics")
               .then().statusCode(200)
               .body("$.size()", greaterThan(0));
    }

    @Test
    void topicUpdate() {
        var channelId = createChannel("topic-upd-test");
        var topicId = given().contentType(ContentType.JSON)
               .body("""
                     {"name": "original"}
                     """)
               .when().post("/api/channels/" + channelId + "/topics")
               .then().statusCode(200)
               .extract().path("id").toString();

        given().contentType(ContentType.JSON)
               .body("""
                     {"name": "renamed"}
                     """)
               .when().put("/api/channels/" + channelId + "/topics/" + topicId)
               .then().statusCode(200);
    }

    // --- Member CRUD ---

    @Test
    void memberJoinAndList() {
        var channelId = createChannel("member-test");
        given().contentType(ContentType.JSON)
               .body("""
                     {"memberId": "alice"}
                     """)
               .when().post("/api/channels/" + channelId + "/members")
               .then().statusCode(200);

        given().when().get("/api/channels/" + channelId + "/members")
               .then().statusCode(200)
               .body("$.size()", greaterThan(0));
    }

    @Test
    void memberLeave() {
        var channelId = createChannel("member-leave-test");
        given().contentType(ContentType.JSON)
               .body("""
                     {"memberId": "bob"}
                     """)
               .when().post("/api/channels/" + channelId + "/members")
               .then().statusCode(200);

        given().when().delete("/api/channels/" + channelId + "/members/bob")
               .then().statusCode(200);
    }

    // --- Presence ---

    @Test
    void presenceListReturnsArray() {
        var channelId = createChannel("presence-test");
        given().when().get("/api/channels/" + channelId + "/presence")
               .then().statusCode(200)
               .body("$.size()", greaterThanOrEqualTo(0));
    }

    // --- Commitments ---

    @Test
    void commitmentsListReturnsArray() {
        var channelId = createChannel("commit-test");
        given().when().get("/api/channels/" + channelId + "/commitments")
               .then().statusCode(200)
               .body("$.size()", greaterThanOrEqualTo(0));
    }

    // --- Correlation ---

    @Test
    void correlationChainReturnsArray() {
        var channelId = createChannel("corr-test");
        given().when().get("/api/channels/" + channelId + "/correlation/no-such-corr")
               .then().statusCode(200)
               .body("$.size()", greaterThanOrEqualTo(0));
    }

    // --- Test helpers ---

    private String createChannel(String name) {
        return given().contentType(ContentType.JSON)
               .body("""
                     {"name": "%s"}
                     """.formatted(name))
               .when().post("/api/channels")
               .then().statusCode(201)
               .extract().path("channelId").toString();
    }

    private String postMessage(String channelId, String text) {
        return given().contentType(ContentType.JSON)
               .body("""
                     {"channelId": "%s", "sender": "test-user",
                      "type": "QUERY", "actorType": "HUMAN", "content": "%s"}
                     """.formatted(channelId, text))
               .when().post("/api/channels/" + channelId + "/messages")
               .then().statusCode(200)
               .extract().path("messageId").toString();
    }
}
