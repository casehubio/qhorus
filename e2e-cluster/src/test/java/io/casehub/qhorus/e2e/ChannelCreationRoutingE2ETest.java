package io.casehub.qhorus.e2e;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChannelCreationRoutingE2ETest {

    private ClusterTestHarness cluster;

    @BeforeAll
    void startCluster() {
        cluster = new ClusterTestHarness("e2e-secret", "node-a", "node-b");
        cluster.start();
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(30));
    }

    @AfterAll
    void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    void channel_created_on_one_node_visible_on_both() {
        String channelId = cluster.createChannel("node-a", "e2e-create-basic");

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response resp = RestAssured.given()
                    .baseUri(cluster.nodeUrl("node-b"))
                    .get("/api/channels/" + channelId);
            assertThat(resp.statusCode()).isEqualTo(200);
        });
    }

    @Test
    void preAssignedId_routes_to_correct_owner() {
        UUID preAssigned = UUID.randomUUID();
        String channelId = cluster.createChannelWithId("node-a",
                "e2e-pre-assigned", preAssigned.toString());

        assertThat(channelId).isEqualTo(preAssigned.toString());

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response respA = RestAssured.given()
                    .baseUri(cluster.nodeUrl("node-a"))
                    .get("/api/channels/" + channelId);
            Response respB = RestAssured.given()
                    .baseUri(cluster.nodeUrl("node-b"))
                    .get("/api/channels/" + channelId);
            assertThat(respA.statusCode()).isEqualTo(200);
            assertThat(respB.statusCode()).isEqualTo(200);
        });
    }

    @Test
    void concurrent_creation_produces_one_channel() {
        String name = "e2e-concurrent-" + UUID.randomUUID().toString().substring(0, 8);

        CompletableFuture<Response> futureA = CompletableFuture.supplyAsync(() ->
                RestAssured.given()
                        .baseUri(cluster.nodeUrl("node-a"))
                        .header("Content-Type", "application/json")
                        .body("{\"name\":\"" + name + "\",\"semantic\":\"APPEND\"}")
                        .post("/api/channels"));

        CompletableFuture<Response> futureB = CompletableFuture.supplyAsync(() ->
                RestAssured.given()
                        .baseUri(cluster.nodeUrl("node-b"))
                        .header("Content-Type", "application/json")
                        .body("{\"name\":\"" + name + "\",\"semantic\":\"APPEND\"}")
                        .post("/api/channels"));

        Response respA = futureA.join();
        Response respB = futureB.join();

        int successes = 0;
        String channelIdA = null;
        String channelIdB = null;
        if (respA.statusCode() == 201) {
            successes++;
            channelIdA = respA.jsonPath().getString("channelId");
        }
        if (respB.statusCode() == 201) {
            successes++;
            channelIdB = respB.jsonPath().getString("channelId");
        }

        if (successes == 2) {
            assertThat(channelIdA).isEqualTo(channelIdB);
        } else {
            assertThat(successes).isGreaterThanOrEqualTo(1);
        }
    }
}
