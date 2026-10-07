package io.casehub.qhorus.cluster;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.ConsumerMessaging;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoutingConsumerMessagingTest {

    private WriteRoutingDecorator router;
    private ConsumerMessaging queries;
    private RoutingConsumerMessaging sut;

    @BeforeEach
    void setUp() {
        router = mock(WriteRoutingDecorator.class);
        queries = mock(ConsumerMessaging.class);
        sut = new RoutingConsumerMessaging(router, queries);
    }

    @Test
    void dispatchDelegatesToRouter() {
        var dispatch = MessageDispatch.builder().channelId(UUID.randomUUID())
                .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        var expected = new DispatchResult(1L, dispatch.channelId(), "agent-1",
                MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of());
        when(router.dispatch(dispatch)).thenReturn(expected);

        DispatchResult result = sut.dispatch(dispatch);

        assertThat(result).isEqualTo(expected);
        verify(router).dispatch(dispatch);
        verifyNoInteractions(queries);
    }

    @Test
    void quorumViolationPropagatesFromRouter() {
        var dispatch = MessageDispatch.builder().channelId(UUID.randomUUID())
                .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        when(router.dispatch(dispatch)).thenThrow(new QuorumViolationException("minority partition"));

        assertThatThrownBy(() -> sut.dispatch(dispatch))
                .isInstanceOf(QuorumViolationException.class);
    }

    @Test
    void historyDelegatesToQueries() {
        UUID channelId = UUID.randomUUID();
        List<Message> expected = List.of();
        when(queries.history(channelId, 0L, 50)).thenReturn(expected);

        List<Message> result = sut.history(channelId, 0L, 50);

        assertThat(result).isSameAs(expected);
        verify(queries).history(channelId, 0L, 50);
        verifyNoInteractions(router);
    }

    @Test
    void findByIdDelegatesToQueries() {
        when(queries.findById(42L)).thenReturn(Optional.empty());

        Optional<Message> result = sut.findById(42L);

        assertThat(result).isEmpty();
        verify(queries).findById(42L);
        verifyNoInteractions(router);
    }
}
