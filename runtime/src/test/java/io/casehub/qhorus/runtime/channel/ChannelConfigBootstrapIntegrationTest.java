package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ChannelConfigProfile.class)
class ChannelConfigBootstrapIntegrationTest {

    @Inject
    ChannelService channelService;

    @Test
    void channelsDeclaredInConfigExistAfterStartup() {
        var reviews = channelService.findByName("it-reviews");
        assertThat(reviews).isPresent();
        assertThat(reviews.get().semantic()).isEqualTo(ChannelSemantic.APPEND);
        assertThat(reviews.get().description()).isEqualTo("Integration test reviews");

        var status = channelService.findByName("it-status");
        assertThat(status).isPresent();
        assertThat(status.get().semantic()).isEqualTo(ChannelSemantic.BROADCAST);
        assertThat(status.get().description()).isEqualTo("Integration test status");
        assertThat(status.get().allowedTypes()).containsExactlyInAnyOrder(MessageType.STATUS, MessageType.EVENT);
    }
}
