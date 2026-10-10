package io.casehub.qhorus.e2e;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CacheCoherenceE2ETest {

    private ClusterTestHarness cluster;
    private String channelId;

    @BeforeAll
    void startCluster() {
        cluster = new ClusterTestHarness("e2e-secret", "node-a", "node-b");
        cluster.start();
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(30));
        channelId = cluster.createChannel("node-a", "e2e-cache");
    }

    @AfterAll
    void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    @Order(1)
    void cross_node_cache_population() {
        cluster.sendMessage("node-a", channelId, "agent-a", "STATUS", "cache-test-1");

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            var stats = cluster.getCacheStats("node-b");
            assertThat(stats.status()).isEqualTo("UP");
            assertThat(stats.channelsCached()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    @Order(2)
    void rapid_messages_converge() {
        for (int i = 0; i < 10; i++) {
            cluster.sendMessage("node-a", channelId, "agent-a", "STATUS", "rapid-" + i);
        }

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response msgs = cluster.getMessages("node-b", channelId);
            assertThat(msgs.jsonPath().getList("$")).hasSizeGreaterThanOrEqualTo(11);
        });
    }

    @Test
    @Order(3)
    void cache_invalidated_on_channel_delete() {
        String tempChannelId = cluster.createChannel("node-a", "e2e-cache-delete");
        cluster.sendMessage("node-a", tempChannelId, "agent-a", "STATUS", "will-delete");

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            var stats = cluster.getCacheStats("node-b");
            assertThat(stats.channelsCached()).isGreaterThanOrEqualTo(2);
        });

        RestAssured.given()
                .baseUri(cluster.nodeUrl("node-a"))
                .queryParam("force", true)
                .delete("/api/channels/" + tempChannelId)
                .then().statusCode(200);

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            Response msgs = cluster.getMessages("node-b", tempChannelId);
            assertThat(msgs.statusCode()).isGreaterThanOrEqualTo(400);
        });
    }
}
