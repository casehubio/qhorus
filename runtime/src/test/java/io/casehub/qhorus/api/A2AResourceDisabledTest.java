package io.casehub.qhorus.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class A2AResourceDisabledTest {

    // -----------------------------------------------------------------------
    // POST /a2a — sync dispatch disabled
    // -----------------------------------------------------------------------

    @Test
    void syncDispatchReturns501WhenA2ADisabled() {
        given()
                .contentType("application/json")
                .accept("application/json")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"message/send\",\"params\":{}}")
                .when().post("/a2a")
                .then()
                .statusCode(501);
    }

    @Test
    void syncDispatchReturns501WithInformativeMessage() {
        given()
                .contentType("application/json")
                .accept("application/json")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"message/send\",\"params\":{}}")
                .when().post("/a2a")
                .then()
                .statusCode(501)
                .body(containsString("a2a"));
    }

    // -----------------------------------------------------------------------
    // POST /a2a — SSE stream disabled
    // -----------------------------------------------------------------------

    @Test
    void streamDispatchReturns200WithErrorEvent() {
        final String body = given()
                                    .contentType("application/json")
                                    .accept("text/event-stream")
                                    .body("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"message/send\",\"params\":{}}")
                                    .when().post("/a2a")
                                    .then()
                                    .statusCode(200)
                                    .contentType("text/event-stream")
                                    .extract().body().asString();

        assertThat(body).contains("event:error");
        assertThat(body).contains("\"final\":true");
    }
}
