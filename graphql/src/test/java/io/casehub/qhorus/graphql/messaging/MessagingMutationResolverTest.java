package io.casehub.qhorus.graphql.messaging;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.graphql.dto.DispatchMessageInput;
import io.casehub.qhorus.graphql.dto.DispatchResultType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingMutationResolverTest {

    private MessagingMutationResolver resolver;
    private MessageDispatcher messageDispatcher;
    private CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        messageDispatcher = mock(MessageDispatcher.class);
        currentPrincipal = mock(CurrentPrincipal.class);
        when(currentPrincipal.actorId()).thenReturn("test-actor");
        when(currentPrincipal.tenancyId()).thenReturn("default");
        resolver = new MessagingMutationResolver();
        resolver.messageDispatcher = messageDispatcher;
        resolver.currentPrincipal = currentPrincipal;
    }

    @Test
    void dispatchMessageBuildsDispatchFromInput() {
        UUID channelId = UUID.randomUUID();
        UUID chId = UUID.randomUUID();
        DispatchResult result = new DispatchResult(1L, chId, "test-actor",
                MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, List.of());
        when(messageDispatcher.dispatch(any(MessageDispatch.class))).thenReturn(result);

        DispatchMessageInput input = new DispatchMessageInput(
                channelId, "STATUS", "hello", null, null, null, null, null);
        DispatchResultType actual = resolver.dispatchMessage(input);

        assertThat(actual).isNotNull();

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        MessageDispatch dispatched = captor.getValue();
        assertThat(dispatched.channelId()).isEqualTo(channelId);
        assertThat(dispatched.sender()).isEqualTo("test-actor");
        assertThat(dispatched.content()).isEqualTo("hello");
    }

    @Test
    void dispatchMessagePassesOptionalFields() {
        UUID channelId = UUID.randomUUID();
        DispatchResult result = new DispatchResult(2L, channelId, "test-actor",
                MessageType.COMMAND, "corr-1", null, List.of(), null, null, null, null, 0, List.of());
        when(messageDispatcher.dispatch(any(MessageDispatch.class))).thenReturn(result);

        DispatchMessageInput input = new DispatchMessageInput(
                channelId, "COMMAND", "do something", "corr-1", null, "role:worker", "general", null);
        resolver.dispatchMessage(input);

        ArgumentCaptor<MessageDispatch> captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());
        MessageDispatch dispatched = captor.getValue();
        assertThat(dispatched.correlationId()).isEqualTo("corr-1");
        assertThat(dispatched.target()).isEqualTo("role:worker");
        assertThat(dispatched.topic()).isEqualTo("general");
    }
}
