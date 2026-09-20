package io.casehub.qhorus.graphql.channels;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelsServiceMutationTest {

    private ChannelsService service;
    private ChannelManager channelManager;

    @BeforeEach
    void setUp() {
        channelManager = mock(ChannelManager.class);
        service = new ChannelsService(mock(ChannelReader.class), mock(ConsumerMessaging.class), channelManager,
                mock(io.casehub.qhorus.api.channel.TopicManager.class),
                mock(io.casehub.qhorus.api.channel.MembershipManager.class),
                mock(io.casehub.qhorus.api.channel.UnreadCountProvider.class),
                mock(io.casehub.qhorus.api.channel.SpaceManager.class),
                mock(io.casehub.qhorus.api.gateway.BackendRegistry.class),
                mock(io.casehub.qhorus.api.store.MessageReader.class),
                mock(io.casehub.platform.api.identity.CurrentPrincipal.class));
    }

    @Test
    void createChannelDelegatesToManager() {
        Channel created = createChannel("new-channel");
        when(channelManager.create(any(ChannelCreateRequest.class))).thenReturn(created);

        ChannelCreateRequest input = ChannelCreateRequest.builder("new-channel").build();
        Channel result = service.createChannel(input);

        assertThat(result.name()).isEqualTo("new-channel");
    }

    @Test
    void deleteChannelDelegatesToManager() {
        UUID id = UUID.randomUUID();
        when(channelManager.delete(id, true)).thenReturn(5L);

        long result = service.deleteChannel(id, true);

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void pauseChannelReturnsUpdatedChannel() {
        UUID id = UUID.randomUUID();
        Channel paused = createChannel("paused-ch");
        when(channelManager.pause(id)).thenReturn(paused);

        Channel result = service.pauseChannel(id);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("paused-ch");
    }

    @Test
    void resumeChannelReturnsUpdatedChannel() {
        UUID id = UUID.randomUUID();
        Channel resumed = createChannel("resumed-ch");
        when(channelManager.resume(id)).thenReturn(resumed);

        Channel result = service.resumeChannel(id);

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
