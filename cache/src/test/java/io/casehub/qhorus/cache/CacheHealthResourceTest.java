package io.casehub.qhorus.cache;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
@TestProfile(CacheCdiWiringTest.EnabledProfile.class)
class CacheHealthResourceTest {

    @Test
    void health_endpoint_returns_up_when_enabled() {
        given()
            .when().get("/health/cache")
            .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("channelsCached", greaterThanOrEqualTo(0))
                .body("messagesCached", greaterThanOrEqualTo(0));
    }
}
