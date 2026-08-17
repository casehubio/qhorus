package io.casehub.qhorus.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.graphql.dto.CreateChannelInput;
import io.casehub.qhorus.graphql.dto.DispatchMessageInput;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QhorusMutationResolverTest {

    private QhorusMutationResolver resolver;
    private ChannelManager channelManager;
    private MessageDispatcher messageDispatcher;
    private CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        resolver = new QhorusMutationResolver();
        channelManager = mock(ChannelManager.class);
        messageDispatcher = mock(MessageDispatcher.class);
        currentPrincipal = mock(CurrentPrincipal.class);

        resolver.channelManager = channelManager;
        resolver.messageDispatcher = messageDispatcher;
        resolver.currentPrincipal = currentPrincipal;

        when(currentPrincipal.tenancyId()).thenReturn("test-tenant");
        when(currentPrincipal.actorId()).thenReturn("test-actor");
    }

    @Test
    void createChannelDelegatesToManager() {
        var input = new CreateChannelInput(
                "new-channel", "A test channel", ChannelSemantic.APPEND,
                null, null, null, null, null, null, null);
        Channel created = Channel.builder("new-channel")
                .id(UUID.randomUUID())
                .semantic(ChannelSemantic.APPEND)
                .tenancyId("test-tenant")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(channelManager.create(any(ChannelCreateRequest.class))).thenReturn(created);

        var result = resolver.createChannel(input);

        assertThat(result.name()).isEqualTo("new-channel");
    }

    @Test
    void deleteChannelDelegatesToManager() {
        UUID channelId = UUID.randomUUID();
        when(channelManager.delete(channelId, false)).thenReturn(5L);

        long count = resolver.deleteChannel(channelId, false);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void pauseChannelReturnsUpdatedChannel() {
        UUID channelId = UUID.randomUUID();
        Channel paused = Channel.builder("ch")
                .id(channelId).paused(true)
                .tenancyId("test-tenant")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(channelManager.pause(channelId)).thenReturn(paused);

        var result = resolver.pauseChannel(channelId);

        assertThat(result.paused()).isTrue();
    }

    @Test
    void resumeChannelReturnsUpdatedChannel() {
        UUID channelId = UUID.randomUUID();
        Channel resumed = Channel.builder("ch")
                .id(channelId).paused(false)
                .tenancyId("test-tenant")
                .createdAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();
        when(channelManager.resume(channelId)).thenReturn(resumed);

        var result = resolver.resumeChannel(channelId);

        assertThat(result.paused()).isFalse();
    }

    @Test
    void dispatchMessageBuildsDispatchFromInput() {
        UUID channelId = UUID.randomUUID();
        var input = new DispatchMessageInput(
                channelId, "STATUS", "hello", null, null, null, null, null);
        var dispatchResult = new DispatchResult(
                42L, channelId, "test-actor", MessageType.STATUS,
                null, null, List.of(), null, null, null, null, 0, List.of());
        when(messageDispatcher.dispatch(any(MessageDispatch.class))).thenReturn(dispatchResult);

        var result = resolver.dispatchMessage(input);

        assertThat(result.messageId()).isEqualTo(42L);
        assertThat(result.sender()).isEqualTo("test-actor");

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        MessageDispatch captured = captor.getValue();
        assertThat(captured.channelId()).isEqualTo(channelId);
        assertThat(captured.sender()).isEqualTo("test-actor");
        assertThat(captured.type()).isEqualTo(MessageType.STATUS);
        assertThat(captured.content()).isEqualTo("hello");
        assertThat(captured.tenancyId()).isEqualTo("test-tenant");
    }
}
