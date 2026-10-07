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
class NodeFailureE2ETest {

    private ClusterTestHarness cluster;
    private String channelId;

    @BeforeAll
    void startCluster() {
        cluster = new ClusterTestHarness("e2e-secret", "node-a", "node-b");
        cluster.start();
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(30));
        channelId = cluster.createChannel("node-a", "e2e-failure-test");
    }

    @AfterAll
    void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    @Order(1)
    void baseline_both_nodes_healthy() {
        assertThat(cluster.getClusterHealth("node-a").statusCode()).isEqualTo(200);
        assertThat(cluster.getClusterHealth("node-b").statusCode()).isEqualTo(200);
    }

    @Test
    @Order(2)
    void stop_node_b_detected_as_dead() {
        cluster.stopNode("node-b");

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            Response health = cluster.getClusterHealth("node-a");
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.jsonPath().getInt("clusterSize")).isEqualTo(1);
        });
    }

    @Test
    @Order(3)
    void surviving_node_still_accepts_writes() {
        Response resp = cluster.sendMessage("node-a", channelId,
                "agent-1", "status", "still-alive");
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Test
    @Order(4)
    void restart_node_b_rejoins_cluster() {
        cluster.startNode("node-b");
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(30));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response msgs = cluster.getMessages("node-b", channelId);
            assertThat(msgs.statusCode()).isEqualTo(200);
        });
    }
}
