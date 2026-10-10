package io.casehub.qhorus.e2e;

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
class NodeFailureFallbackE2ETest {

    private ClusterTestHarness cluster;
    private String channelId;

    @BeforeAll
    void startCluster() {
        cluster = new ClusterTestHarness("e2e-secret", "node-a", "node-b");
        cluster.start();
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(30));
        channelId = cluster.createChannel("node-a", "e2e-fallback");
    }

    @AfterAll
    void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    @Order(1)
    void fallback_to_local_when_owner_down() {
        cluster.stopNode("node-a");

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            Response health = cluster.getClusterHealth("node-b");
            assertThat(health.jsonPath().getInt("clusterSize")).isEqualTo(1);
        });

        Response resp = cluster.sendMessage("node-b", channelId,
                "agent-b", "STATUS", "fallback-dispatch");
        assertThat(resp.statusCode()).isEqualTo(200);

        Response msgs = cluster.getMessages("node-b", channelId);
        assertThat(msgs.jsonPath().getList("content", String.class))
                .contains("fallback-dispatch");
    }

    @Test
    @Order(2)
    void ownership_reconstructs_after_recovery() {
        cluster.startNode("node-a");
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(60));

        for (int i = 0; i < 10; i++) {
            cluster.sendMessage("node-a", channelId, "agent-a", "STATUS", "recover-" + i);
        }

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            Response claimsA = cluster.getLocalClaims("node-a");
            assertThat(claimsA.statusCode()).isEqualTo(200);
        });
    }

    @Test
    @Order(3)
    void post_recovery_routing_works() {
        Response resp = cluster.sendMessage("node-b", channelId,
                "agent-b", "STATUS", "post-recovery");
        assertThat(resp.statusCode()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response msgs = cluster.getMessages("node-a", channelId);
            assertThat(msgs.jsonPath().getList("content", String.class))
                    .contains("post-recovery");
        });
    }
}
