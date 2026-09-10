package io.casehub.qhorus.graphql;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.qhorus.graphql.channels.ChannelsModelEnricher;
import io.casehub.qhorus.graphql.channels.ChannelsMutationResolver;
import io.casehub.qhorus.graphql.channels.ChannelsQueryResolver;
import io.casehub.qhorus.graphql.channels.ChannelsSubscriptionResolver;
import io.casehub.qhorus.graphql.governance.GovernanceQueryResolver;
import io.casehub.qhorus.graphql.messaging.MessagingMutationResolver;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainRegistrationTest {

    @Test
    void channelsDomainAnnotationsPresent() {
        assertDomain(ChannelsQueryResolver.class, "channels", true);
        assertDomain(ChannelsMutationResolver.class, "channels", true);
        assertDomain(ChannelsSubscriptionResolver.class, "channels", true);
        assertDomain(ChannelsModelEnricher.class, "channels", false);
    }

    @Test
    void governanceDomainAnnotationsPresent() {
        assertDomain(GovernanceQueryResolver.class, "governance", true);
    }

    @Test
    void messagingDomainAnnotationsPresent() {
        assertDomain(MessagingMutationResolver.class, "messaging", true);
    }

    @Test
    void noQhorusDomainRemains() {
        Class<?>[] classes = {
                ChannelsQueryResolver.class, ChannelsMutationResolver.class,
                ChannelsSubscriptionResolver.class, ChannelsModelEnricher.class,
                GovernanceQueryResolver.class, MessagingMutationResolver.class
        };
        for (Class<?> cls : classes) {
            McpDomain ann = cls.getAnnotation(McpDomain.class);
            assertThat(ann.value())
                    .describedAs("Class %s should not use 'qhorus' domain", cls.getSimpleName())
                    .isNotEqualTo("qhorus");
        }
    }

    private void assertDomain(Class<?> cls, String expectedDomain, boolean expectGraphQLApi) {
        McpDomain mcpDomain = cls.getAnnotation(McpDomain.class);
        assertThat(mcpDomain)
                .describedAs("@McpDomain missing on %s", cls.getSimpleName())
                .isNotNull();
        assertThat(mcpDomain.value()).isEqualTo(expectedDomain);

        if (expectGraphQLApi) {
            assertThat(cls.getAnnotation(GraphQLApi.class))
                    .describedAs("@GraphQLApi missing on %s", cls.getSimpleName())
                    .isNotNull();
        }
    }
}
