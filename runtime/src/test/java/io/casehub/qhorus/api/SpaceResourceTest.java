package io.casehub.qhorus.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SpaceResourceTest {

    @Test
    void listRootsReturnsArray() {
        given().when().get("/api/spaces")
            .then().statusCode(200)
            .body("$.size()", greaterThanOrEqualTo(0));
    }

    @Test
    void createAndGetSpace() {
        var id = given().contentType(ContentType.JSON)
            .body("""
                {"name": "space-rest-test", "description": "A test space"}
                """)
            .when().post("/api/spaces")
            .then()
            .statusCode(200)
            .body("name", equalTo("space-rest-test"))
            .body("description", equalTo("A test space"))
            .body("id", notNullValue())
            .extract().path("id").toString();

        given().when().get("/api/spaces/" + id)
            .then().statusCode(200)
            .body("name", equalTo("space-rest-test"))
            .body("description", equalTo("A test space"));
    }

    @Test
    void getSpaceNotFoundReturns404() {
        given().when().get("/api/spaces/00000000-0000-0000-0000-000000000099")
            .then().statusCode(404);
    }

    @Test
    void nestedSpacesAppearInChildren() {
        var parentId = createSpace("space-parent-test", null);
        var childId = createSpace("space-child-test", parentId);

        given().when().get("/api/spaces/" + parentId + "/children")
            .then().statusCode(200)
            .body("$.size()", greaterThanOrEqualTo(1))
            .body("[0].name", equalTo("space-child-test"));
    }

    @Test
    void deleteSpaceReturns204() {
        var id = createSpace("space-delete-test", null);
        given().when().delete("/api/spaces/" + id)
            .then().statusCode(204);
        given().when().get("/api/spaces/" + id)
            .then().statusCode(404);
    }

    @Test
    void updateSpaceRenames() {
        var id = createSpace("space-rename-orig", null);
        given().contentType(ContentType.JSON)
            .body("""
                {"name": "space-rename-updated"}
                """)
            .when().put("/api/spaces/" + id)
            .then().statusCode(200);

        given().when().get("/api/spaces/" + id)
            .then().statusCode(200)
            .body("name", equalTo("space-rename-updated"));
    }

    @Test
    void channelsInSpaceReturnsFilteredList() {
        var spaceId = createSpace("space-ch-filter-test", null);

        given().contentType(ContentType.JSON)
            .body("""
                {"name": "ch-in-space-test", "spaceId": "%s"}
                """.formatted(spaceId))
            .when().post("/api/channels")
            .then().statusCode(201);

        given().when().get("/api/spaces/" + spaceId + "/channels")
            .then().statusCode(200)
            .body("$.size()", greaterThanOrEqualTo(1));
    }

    private String createSpace(String name, String parentId) {
        var body = parentId != null
            ? """
              {"name": "%s", "parentSpaceId": "%s"}
              """.formatted(name, parentId)
            : """
              {"name": "%s"}
              """.formatted(name);
        return given().contentType(ContentType.JSON).body(body)
            .when().post("/api/spaces")
            .then().statusCode(200)
            .extract().path("id").toString();
    }
}
