package io.casehub.qhorus.e2e;

import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DispatchRoutingE2ETest {

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
    void message_sent_on_one_node_visible_on_other() {
        String channelId = cluster.createChannel("node-a", "e2e-routing-test");

        Response sendResp = cluster.sendMessage("node-a", channelId,
                "agent-1", "status", "hello from node-a");
        assertThat(sendResp.statusCode()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response msgs = cluster.getMessages("node-b", channelId);
            assertThat(msgs.statusCode()).isEqualTo(200);
            List<?> messages = msgs.jsonPath().getList("$");
            assertThat(messages).isNotEmpty();
        });
    }

    @Test
    void messages_from_both_nodes_converge() {
        String channelId = cluster.createChannel("node-a", "e2e-convergence");

        cluster.sendMessage("node-a", channelId, "agent-1", "status", "msg-from-a");
        cluster.sendMessage("node-b", channelId, "agent-2", "status", "msg-from-b");

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response msgsA = cluster.getMessages("node-a", channelId);
            Response msgsB = cluster.getMessages("node-b", channelId);
            assertThat(msgsA.jsonPath().getList("$")).hasSizeGreaterThanOrEqualTo(2);
            assertThat(msgsB.jsonPath().getList("$")).hasSizeGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void cluster_health_reports_both_nodes_alive() {
        Response healthA = cluster.getClusterHealth("node-a");
        Response healthB = cluster.getClusterHealth("node-b");

        assertThat(healthA.statusCode()).isEqualTo(200);
        assertThat(healthB.statusCode()).isEqualTo(200);
        assertThat(healthA.jsonPath().getInt("clusterSize")).isEqualTo(2);
        assertThat(healthB.jsonPath().getInt("clusterSize")).isEqualTo(2);
    }
}
