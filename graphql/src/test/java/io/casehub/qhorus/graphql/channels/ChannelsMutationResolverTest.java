package io.casehub.qhorus.graphql.channels;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.graphql.dto.ChannelType;
import io.casehub.qhorus.graphql.dto.CreateChannelInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelsMutationResolverTest {

    private ChannelsMutationResolver resolver;
    private ChannelManager channelManager;

    @BeforeEach
    void setUp() {
        channelManager = mock(ChannelManager.class);
        resolver = new ChannelsMutationResolver();
        resolver.channelManager = channelManager;
    }

    @Test
    void createChannelDelegatesToManager() {
        Channel created = createChannel("new-channel");
        when(channelManager.create(any(ChannelCreateRequest.class))).thenReturn(created);

        CreateChannelInput input = new CreateChannelInput("new-channel", null,
                null, null, null, null, null, null, null, null);
        ChannelType result = resolver.createChannel(input);

        assertThat(result.name()).isEqualTo("new-channel");
    }

    @Test
    void deleteChannelDelegatesToManager() {
        UUID id = UUID.randomUUID();
        when(channelManager.delete(id, true)).thenReturn(5L);

        long result = resolver.deleteChannel(id, true);

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void pauseChannelReturnsUpdatedChannel() {
        UUID id = UUID.randomUUID();
        Channel paused = createChannel("paused-ch");
        when(channelManager.pause(id)).thenReturn(paused);

        ChannelType result = resolver.pauseChannel(id);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("paused-ch");
    }

    @Test
    void resumeChannelReturnsUpdatedChannel() {
        UUID id = UUID.randomUUID();
        Channel resumed = createChannel("resumed-ch");
        when(channelManager.resume(id)).thenReturn(resumed);

        ChannelType result = resolver.resumeChannel(id);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("resumed-ch");
    }

    private Channel createChannel(String name) {
        return Channel.builder(name)
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .tenancyId("test-tenant")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
    }
}
