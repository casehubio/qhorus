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
class OwnershipTransferE2ETest {

    private ClusterTestHarness cluster;
    private String channelId;

    @BeforeAll
    void startCluster() {
        cluster = new ClusterTestHarness("e2e-secret", "node-a", "node-b");
        cluster.start();
        cluster.waitForClusterConvergence(2, Duration.ofSeconds(30));
        channelId = cluster.createChannel("node-a", "e2e-ownership");
    }

    @AfterAll
    void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    @Order(1)
    void baseline_ownership_is_hash_ring() {
        var ownership = cluster.getOwnership("node-a", channelId);
        assertThat(ownership.source()).isEqualTo("hash-ring");
    }

    @Test
    @Order(2)
    void write_pattern_shift_transfers_ownership() {
        for (int i = 0; i < 20; i++) {
            cluster.sendMessage("node-b", channelId, "agent-b", "STATUS", "claim-" + i);
        }

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            var ownership = cluster.getOwnership("node-b", channelId);
            assertThat(ownership.source()).isEqualTo("dynamic-claim");
            assertThat(ownership.owner()).isEqualTo("node-b");
        });
    }

    @Test
    @Order(3)
    void post_transfer_message_from_old_owner_succeeds() {
        Response resp = cluster.sendMessage("node-a", channelId,
                "agent-a", "STATUS", "proxied-after-transfer");
        assertThat(resp.statusCode()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            var ownershipA = cluster.getOwnership("node-a", channelId);
            var ownershipB = cluster.getOwnership("node-b", channelId);
            assertThat(ownershipA.owner()).isEqualTo(ownershipB.owner());
        });
    }

    @Test
    @Order(4)
    void ownership_reverts_when_writes_stop() {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() -> {
            var ownership = cluster.getOwnership("node-a", channelId);
            assertThat(ownership.source()).isEqualTo("hash-ring");
        });
    }
}
