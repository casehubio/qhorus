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
class QuorumEnforcementE2ETest {

    private ClusterTestHarness cluster;
    private String channelId;

    @BeforeAll
    void startCluster() {
        cluster = new ClusterTestHarness("e2e-secret", "node-a", "node-b", "node-c");
        cluster.start();
        cluster.waitForClusterConvergence(3, Duration.ofSeconds(45));
        channelId = cluster.createChannel("node-a", "e2e-quorum");
    }

    @AfterAll
    void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    @Order(1)
    void full_cluster_accepts_writes() {
        Response resp = cluster.sendMessage("node-a", channelId,
                "agent-1", "STATUS", "quorum-ok");
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    @Test
    @Order(2)
    void minority_partition_rejects_writes() {
        cluster.stopNode("node-b");
        cluster.stopNode("node-c");

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            Response health = cluster.getClusterHealth("node-a");
            assertThat(health.jsonPath().getInt("clusterSize")).isEqualTo(1);
        });

        Response resp = cluster.sendMessage("node-a", channelId,
                "agent-1", "STATUS", "should-be-rejected");
        assertThat(resp.statusCode()).isGreaterThanOrEqualTo(400);
    }

    @Test
    @Order(3)
    void majority_restored_accepts_writes() {
        cluster.startNode("node-b");
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(30));

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response resp = cluster.sendMessage("node-a", channelId,
                    "agent-1", "STATUS", "quorum-restored");
            assertThat(resp.statusCode()).isEqualTo(200);
        });
    }
}
