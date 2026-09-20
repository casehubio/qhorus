package io.casehub.qhorus.graphql;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.api.spi.agents.AgentsApi;
import io.casehub.qhorus.api.spi.channels.ChannelsApi;
import io.casehub.qhorus.api.spi.data.DataApi;
import io.casehub.qhorus.api.spi.governance.GovernanceApi;
import io.casehub.qhorus.api.spi.messaging.MessagingApi;
import io.casehub.qhorus.graphql.channels.ChannelsModelEnricher;
import io.casehub.qhorus.graphql.channels.ChannelsSubscriptionResolver;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainRegistrationTest {

    @Test
    void channelsSpiAnnotationsPresent() {
        assertDomain(ChannelsApi.class, "channels");
    }

    @Test
    void channelsSubscriptionStillHandWritten() {
        assertThat(ChannelsSubscriptionResolver.class.getAnnotation(GraphQLApi.class))
                .describedAs("@GraphQLApi missing on ChannelsSubscriptionResolver")
                .isNotNull();
        assertThat(ChannelsSubscriptionResolver.class.getAnnotation(McpDomain.class).value())
                .isEqualTo("channels");
    }

    @Test
    void channelsModelEnricherAnnotated() {
        assertThat(ChannelsModelEnricher.class.getAnnotation(McpDomain.class).value())
                .isEqualTo("channels");
    }

    @Test
    void governanceSpiAnnotationsPresent() {
        assertDomain(GovernanceApi.class, "governance");
    }

    @Test
    void messagingSpiAnnotationsPresent() {
        assertDomain(MessagingApi.class, "messaging");
    }

    @Test
    void agentsSpiAnnotationsPresent() {
        assertDomain(AgentsApi.class, "agents");
    }

    @Test
    void dataSpiAnnotationsPresent() {
        assertDomain(DataApi.class, "data");
    }


    @Test
    void noQhorusDomainRemains() {
        Class<?>[] spiInterfaces = {ChannelsApi.class, GovernanceApi.class, MessagingApi.class, AgentsApi.class, DataApi.class};
        for (Class<?> cls : spiInterfaces) {
            McpDomain ann = cls.getAnnotation(McpDomain.class);
            assertThat(ann.value())
                    .describedAs("Interface %s should not use 'qhorus' domain", cls.getSimpleName())
                    .isNotEqualTo("qhorus");
        }
    }

    private void assertDomain(Class<?> cls, String expectedDomain) {
        McpDomain mcpDomain = cls.getAnnotation(McpDomain.class);
        assertThat(mcpDomain)
                .describedAs("@McpDomain missing on %s", cls.getSimpleName())
                .isNotNull();
        assertThat(mcpDomain.value()).isEqualTo(expectedDomain);
    }
}
