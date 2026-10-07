package io.casehub.qhorus.cluster;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ClusterDisabledTest.DisabledProfile.class)
class ClusterDisabledTest {

    @Inject Instance<ClusterManager> clusterManager;
    @Inject Instance<HeartbeatService> heartbeatService;
    @Inject Instance<HeartbeatScheduler> heartbeatScheduler;
    @Inject Instance<WriteProxyClient> writeProxyClient;
    @Inject Instance<WriteFrequencyTracker> writeFrequencyTracker;
    @Inject Instance<OwnershipScheduler> ownershipScheduler;
    @Inject Instance<InternalSecretFilter> secretFilter;
    @Inject Instance<ClusterShutdownHandler> shutdownHandler;

    @Test
    void relay_beans_not_resolvable_when_disabled() {
        assertThat(clusterManager.isResolvable()).isFalse();
        assertThat(heartbeatService.isResolvable()).isFalse();
        assertThat(heartbeatScheduler.isResolvable()).isFalse();
        assertThat(writeProxyClient.isResolvable()).isFalse();
        assertThat(writeFrequencyTracker.isResolvable()).isFalse();
        assertThat(ownershipScheduler.isResolvable()).isFalse();
        assertThat(secretFilter.isResolvable()).isFalse();
        assertThat(shutdownHandler.isResolvable()).isFalse();
    }

    public static class DisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.datasource.qhorus.db-kind", "h2",
                    "quarkus.datasource.qhorus.username", "sa",
                    "quarkus.datasource.qhorus.password", "",
                    "quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:cluster_disabled;DB_CLOSE_DELAY=-1",
                    "quarkus.datasource.qhorus.reactive", "false",
                    "quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create"
            );
        }
    }
}
