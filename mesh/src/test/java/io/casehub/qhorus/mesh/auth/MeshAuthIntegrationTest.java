package io.casehub.qhorus.mesh.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(MeshAuthIntegrationTest.AuthProfile.class)
class MeshAuthIntegrationTest {

    public static class AuthProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.ofEntries(
                Map.entry("casehub.qhorus.api-keys.test-agent.key", "test-key-123"),
                Map.entry("casehub.qhorus.default-tenancy-id", "test-tenant"),
                Map.entry("quarkus.oidc.tenant-enabled", "false"),
                Map.entry("quarkus.http.auth.permission.default.paths", "/*"),
                Map.entry("quarkus.http.auth.permission.default.policy", "authenticated"),
                Map.entry("quarkus.http.auth.permission.internal.paths", "/internal/*"),
                Map.entry("quarkus.http.auth.permission.internal.policy", "permit"),
                Map.entry("quarkus.http.auth.permission.health.paths", "/q/*"),
                Map.entry("quarkus.http.auth.permission.health.policy", "permit"),
                Map.entry("quarkus.datasource.qhorus.db-kind", "h2"),
                Map.entry("quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:mesh-auth-test;DB_CLOSE_DELAY=-1"),
                Map.entry("quarkus.datasource.qhorus.username", "sa"),
                Map.entry("quarkus.datasource.qhorus.password", ""),
                Map.entry("quarkus.datasource.qhorus.reactive", "false"),
                Map.entry("quarkus.datasource.db-kind", "h2"),
                Map.entry("quarkus.datasource.jdbc.url", "jdbc:h2:mem:mesh-auth-default-test;DB_CLOSE_DELAY=-1"),
                Map.entry("quarkus.datasource.username", "sa"),
                Map.entry("quarkus.datasource.password", ""),
                Map.entry("quarkus.hibernate-orm.database.generation", "drop-and-create"),
                Map.entry("quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create"),
                Map.entry("quarkus.hibernate-orm.qhorus.sql-load-script", "import-qhorus-test.sql")
            );
        }
    }

    @Test
    void request_without_credentials_is_rejected() {
        RestAssured.given()
            .get("/.well-known/agent.json")
            .then()
            .statusCode(401);
    }

    @Test
    void request_with_invalid_key_is_rejected() {
        RestAssured.given()
            .header("X-Api-Key", "wrong-key")
            .get("/.well-known/agent.json")
            .then()
            .statusCode(401);
    }

    @Test
    void request_with_valid_key_reaches_endpoint() {
        RestAssured.given()
            .header("X-Api-Key", "test-key-123")
            .get("/.well-known/agent.json")
            .then()
            .statusCode(200);
    }

    @Test
    void health_endpoints_remain_open_without_credentials() {
        RestAssured.given()
            .get("/q/health")
            .then()
            .statusCode(anyOf(is(200), is(204)));
    }

    @Test
    void authenticated_request_populates_current_principal() {
        RestAssured.given()
            .header("X-Api-Key", "test-key-123")
            .get("/test/identity")
            .then()
            .statusCode(200)
            .body("actorId", is("test-agent"))
            .body("tenancyId", is("test-tenant"));
    }
}
