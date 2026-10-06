package io.casehub.qhorus.runtime.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class MessageListingTest {

    @Test
    void postAndListMessages() {
        given().contentType(ContentType.JSON)
                .body("{\"name\": \"msg-list-rest\", \"semantic\": \"APPEND\"}")
                .when().post("/api/channels")
                .then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("{\"sender\": \"agent-1\", \"type\": \"STATUS\", \"actorType\": \"AGENT\", \"content\": \"hello\"}")
                .when().post("/api/channels/msg-list-rest/messages")
                .then().statusCode(200);

        given().when().get("/api/channels/msg-list-rest/messages")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].sender", equalTo("agent-1"))
                .body("[0].content", equalTo("hello"))
                .body("[0].type", equalTo("STATUS"));
    }

    @Test
    void listMessagesWithPagination() {
        given().contentType(ContentType.JSON)
                .body("{\"name\": \"msg-page-rest\", \"semantic\": \"APPEND\"}")
                .when().post("/api/channels").then().statusCode(201);

        for (int i = 0; i < 3; i++) {
            given().contentType(ContentType.JSON)
                    .body("{\"sender\": \"agent-1\", \"type\": \"STATUS\", \"actorType\": \"AGENT\", \"content\": \"msg-" + i + "\"}")
                    .when().post("/api/channels/msg-page-rest/messages")
                    .then().statusCode(200);
        }

        var firstPage = given().queryParam("limit", 2)
                .when().get("/api/channels/msg-page-rest/messages")
                .then().statusCode(200)
                .body("size()", equalTo(2))
                .extract().jsonPath();

        Long lastId = firstPage.getLong("[1].id");
        given().queryParam("afterId", lastId)
                .when().get("/api/channels/msg-page-rest/messages")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));
    }

    @Test
    void listMessagesWithTypeFilter() {
        given().contentType(ContentType.JSON)
                .body("{\"name\": \"msg-type-rest\", \"semantic\": \"APPEND\"}")
                .when().post("/api/channels").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("{\"sender\": \"agent-1\", \"type\": \"STATUS\", \"actorType\": \"AGENT\", \"content\": \"status msg\"}")
                .when().post("/api/channels/msg-type-rest/messages").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"sender\": \"agent-1\", \"type\": \"EVENT\", \"actorType\": \"AGENT\"}")
                .when().post("/api/channels/msg-type-rest/messages").then().statusCode(200);

        given().queryParam("type", "STATUS")
                .when().get("/api/channels/msg-type-rest/messages")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].type", equalTo("STATUS"));
    }

    @Test
    void listMessagesForNonExistentChannelReturns404() {
        given().when().get("/api/channels/no-such-channel/messages")
                .then().statusCode(404);
    }
}
