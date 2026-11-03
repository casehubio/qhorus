package io.casehub.qhorus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class VisibilityResourceTest {

    @Test
    void createDirect_returns201() {
        String suffix = String.valueOf(System.nanoTime());
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"participantA": "rest-agent-a-%s", "participantB": "rest-agent-b-%s"}
                        """.formatted(suffix, suffix))
                .when().post("/api/visibility/direct")
                .then()
                .statusCode(201)
                .body("name", equalTo("dm/rest-agent-a-" + suffix + "/rest-agent-b-" + suffix))
                .body("visibility", equalTo("DIRECT"))
                .body("channelId", notNullValue());
    }

    @Test
    void createDirect_existing_returns200() {
        String suffix = String.valueOf(System.nanoTime());
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"participantA": "rest-dup-a-%s", "participantB": "rest-dup-b-%s"}
                        """.formatted(suffix, suffix))
                .when().post("/api/visibility/direct")
                .then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"participantA": "rest-dup-b-%s", "participantB": "rest-dup-a-%s"}
                        """.formatted(suffix, suffix))
                .when().post("/api/visibility/direct")
                .then()
                .statusCode(200)
                .body("visibility", equalTo("DIRECT"));
    }

    @Test
    void createDirect_sameParticipant_returns500() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"participantA": "same-agent", "participantB": "same-agent"}
                        """)
                .when().post("/api/visibility/direct")
                .then()
                .statusCode(500);
    }

    @Test
    void findDirect_returnsDirectChannels() {
        String suffix = String.valueOf(System.nanoTime());
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"participantA": "rest-find-a-%s", "participantB": "rest-find-b-%s"}
                        """.formatted(suffix, suffix))
                .when().post("/api/visibility/direct")
                .then().statusCode(201);

        given()
                .when().get("/api/visibility/direct/rest-find-a-" + suffix)
                .then()
                .statusCode(200)
                .body("", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].visibility", equalTo("DIRECT"));
    }

    @Test
    void createGroup_returns201() {
        UUID groupId = UUID.randomUUID();
        String suffix = String.valueOf(System.nanoTime());
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"groupId": "%s", "groupName": "rest-team-%s",
                         "memberIds": ["rest-coord-%s", "rest-member-%s"],
                         "coordinatorId": "rest-coord-%s"}
                        """.formatted(groupId, suffix, suffix, suffix, suffix))
                .when().post("/api/visibility/group")
                .then()
                .statusCode(201)
                .body("name", equalTo("group/rest-team-" + suffix))
                .body("visibility", equalTo("PRIVATE"))
                .body("boundGroupId", equalTo(groupId.toString()));
    }

    @Test
    void findGroup_existing_returns200() {
        UUID groupId = UUID.randomUUID();
        String suffix = String.valueOf(System.nanoTime());
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"groupId": "%s", "groupName": "rest-grp-%s",
                         "memberIds": ["rest-c-%s"], "coordinatorId": "rest-c-%s"}
                        """.formatted(groupId, suffix, suffix, suffix))
                .when().post("/api/visibility/group")
                .then().statusCode(201);

        given()
                .when().get("/api/visibility/group/" + groupId)
                .then()
                .statusCode(200)
                .body("boundGroupId", equalTo(groupId.toString()));
    }

    @Test
    void findGroup_notFound_returns404() {
        given()
                .when().get("/api/visibility/group/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void setVisibility_viaChannelResource() {
        String name = "vis-rest-set-" + System.nanoTime();
        String channelId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s"}
                        """.formatted(name))
                .when().post("/api/channels")
                .then().statusCode(201)
                .extract().path("channelId");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"visibility": "PRIVATE"}
                        """)
                .when().put("/api/channels/" + channelId + "/visibility")
                .then()
                .statusCode(200)
                .body("visibility", equalTo("PRIVATE"));
    }

    @Test
    void listChannels_filterByVisibility() {
        String suffix = String.valueOf(System.nanoTime());
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "vis-list-pub-%s", "visibility": "PUBLIC", "semantic": "BROADCAST"}
                        """.formatted(suffix))
                .when().post("/api/channels")
                .then().statusCode(201);

        given()
                .queryParam("visibility", "PUBLIC")
                .when().get("/api/channels")
                .then()
                .statusCode(200)
                .body("findAll { it.name == 'vis-list-pub-" + suffix + "' }.size()", equalTo(1));
    }
}
