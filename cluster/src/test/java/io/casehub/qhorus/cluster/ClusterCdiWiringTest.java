package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ClusterCdiWiringTest.EnabledProfile.class)
class ClusterCdiWiringTest {

    @Inject Instance<ClusterManager> clusterManager;
    @Inject Instance<HeartbeatService> heartbeatService;
    @Inject Instance<HeartbeatScheduler> heartbeatScheduler;
    @Inject Instance<WriteProxyClient> writeProxyClient;
    @Inject Instance<WriteFrequencyTracker> writeFrequencyTracker;
    @Inject Instance<OwnershipScheduler> ownershipScheduler;
    @Inject Instance<InternalSecretFilter> secretFilter;
    @Inject Instance<ClusterShutdownHandler> shutdownHandler;

    @Inject MessageDispatcher messageDispatcher;
    @Inject ConsumerMessaging consumerMessaging;
    @Inject ChannelManager channelManager;

    @Test
    void all_relay_beans_are_resolvable() {
        assertThat(clusterManager.isResolvable()).isTrue();
        assertThat(heartbeatService.isResolvable()).isTrue();
        assertThat(heartbeatScheduler.isResolvable()).isTrue();
        assertThat(writeProxyClient.isResolvable()).isTrue();
        assertThat(writeFrequencyTracker.isResolvable()).isTrue();
        assertThat(ownershipScheduler.isResolvable()).isTrue();
        assertThat(secretFilter.isResolvable()).isTrue();
        assertThat(shutdownHandler.isResolvable()).isTrue();
    }

    @Test
    void message_dispatcher_is_routing_consumer_messaging() {
        assertThat(ClientProxy.unwrap(messageDispatcher)).isInstanceOf(RoutingConsumerMessaging.class);
    }

    @Test
    void consumer_messaging_is_routing_consumer_messaging() {
        assertThat(ClientProxy.unwrap(consumerMessaging)).isInstanceOf(RoutingConsumerMessaging.class);
    }


    @Test
    void channel_manager_is_decorator() {
        assertThat(ClientProxy.unwrap(channelManager)).isInstanceOf(ChannelManagerDecorator.class);
    }

    public static class EnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "casehub.qhorus.relay.enabled", "true",
                    "casehub.qhorus.relay.routing", "dynamic",
                    "casehub.qhorus.relay.peers", "node-a:8080,node-b:8080",
                    "quarkus.datasource.qhorus.db-kind", "h2",
                    "quarkus.datasource.qhorus.username", "sa",
                    "quarkus.datasource.qhorus.password", "",
                    "quarkus.datasource.qhorus.jdbc.url", "jdbc:h2:mem:cluster_enabled;DB_CLOSE_DELAY=-1",
                    "quarkus.datasource.qhorus.reactive", "false",
                    "quarkus.hibernate-orm.qhorus.database.generation", "drop-and-create"
            );
        }
    }
}
