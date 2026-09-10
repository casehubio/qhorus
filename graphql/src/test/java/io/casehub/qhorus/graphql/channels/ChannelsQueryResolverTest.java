package io.casehub.qhorus.graphql.channels;

import io.casehub.platform.graphql.PageInput;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelReader;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.graphql.dto.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelsQueryResolverTest {

    private ChannelsQueryResolver resolver;
    private ChannelReader channelReader;
    private ConsumerMessaging consumerMessaging;

    @BeforeEach
    void setUp() {
        resolver = new ChannelsQueryResolver();
        channelReader = mock(ChannelReader.class);
        consumerMessaging = mock(ConsumerMessaging.class);
        resolver.channelReader = channelReader;
        resolver.consumerMessaging = consumerMessaging;
    }

    @Test
    void channelsReturnsPaginatedResults() {
        Channel ch = createChannel("test-channel");
        when(channelReader.scan(ArgumentMatchers.any())).thenReturn(List.of(ch));

        var result = resolver.channels(null, new PageInput(0, 10, null));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("test-channel");
        assertThat(result.pageInfo().totalCount()).isEqualTo(1);
    }

    @Test
    void channelByIdReturnsChannel() {
        UUID id = UUID.randomUUID();
        Channel ch = createChannel("found");
        when(channelReader.findById(id)).thenReturn(Optional.of(ch));

        ChannelType result = resolver.channel(id, null);

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("found");
    }

    @Test
    void channelByNameReturnsChannel() {
        Channel ch = createChannel("by-name");
        when(channelReader.findByName("by-name")).thenReturn(Optional.of(ch));

        ChannelType result = resolver.channel(null, "by-name");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("by-name");
    }

    @Test
    void channelReturnsNullWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(channelReader.findById(id)).thenReturn(Optional.empty());

        ChannelType result = resolver.channel(id, null);

        assertThat(result).isNull();
    }

    @Test
    void channelThrowsWhenNeitherIdNorName() {
        assertThatThrownBy(() -> resolver.channel(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void channelMessagesReturnsHistory() {
        UUID channelId = UUID.randomUUID();
        Message msg = createMessage(channelId, 1L);
        when(consumerMessaging.history(channelId, 0L, 50)).thenReturn(List.of(msg));

        var result = resolver.channelMessages(channelId, null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void channelMessagesUsesProvidedCursor() {
        UUID channelId = UUID.randomUUID();
        when(consumerMessaging.history(channelId, 100L, 25)).thenReturn(List.of());

        var result = resolver.channelMessages(channelId, 100L, 25);

        assertThat(result).isEmpty();
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

    private Message createMessage(UUID channelId, Long id) {
        return Message.builder()
                .id(id)
                .channelId(channelId)
                .sender("test-actor")
                .messageType(MessageType.STATUS)
                .content("test content")
                .createdAt(Instant.now())
                .build();
    }
}
