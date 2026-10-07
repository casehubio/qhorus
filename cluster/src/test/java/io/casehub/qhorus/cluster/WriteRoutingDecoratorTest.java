package io.casehub.qhorus.cluster;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WriteRoutingDecoratorTest {

    private MessageDispatcher delegate;
    private ClusterManager clusterManager;
    private WriteProxyClient proxyClient;
    private WriteRoutingDecorator decorator;
    private static final UUID CHANNEL_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @BeforeEach
    void setUp() {
        delegate = mock(MessageDispatcher.class);
        clusterManager = mock(ClusterManager.class);
        proxyClient = mock(WriteProxyClient.class);
        decorator = new WriteRoutingDecorator(delegate, clusterManager, proxyClient, true);
    }

    @Test
    void delegatesLocallyWhenOwner() {
        var localNode = new NodeInfo("node-1", "node-1:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(CHANNEL_ID)).thenReturn(localNode);
        when(clusterManager.isLocal(localNode)).thenReturn(true);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        var expectedResult = new DispatchResult(1L, CHANNEL_ID, "agent-1",
                MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of());
        when(delegate.dispatch(dispatch)).thenReturn(expectedResult);

        DispatchResult result = decorator.dispatch(dispatch);

        assertThat(result).isEqualTo(expectedResult);
        verify(delegate).dispatch(dispatch);
        verifyNoInteractions(proxyClient);
    }

    @Test
    void proxiesToRemoteWhenNotOwner() {
        var remoteNode = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(CHANNEL_ID)).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        var expectedResult = new DispatchResult(1L, CHANNEL_ID, "agent-1",
                MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of());
        when(proxyClient.dispatch(remoteNode, dispatch)).thenReturn(expectedResult);

        DispatchResult result = decorator.dispatch(dispatch);

        assertThat(result).isEqualTo(expectedResult);
        verify(proxyClient).dispatch(remoteNode, dispatch);
        verifyNoInteractions(delegate);
    }

    @Test
    void throwsOnQuorumViolation() {
        when(clusterManager.canServeWrites()).thenReturn(false);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();

        assertThatThrownBy(() -> decorator.dispatch(dispatch))
                .isInstanceOf(QuorumViolationException.class);
    }

    @Test
    void routingDisabledDelegatesDirectly() {
        var passThrough = new WriteRoutingDecorator(delegate, clusterManager, proxyClient, false);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        var expectedResult = new DispatchResult(1L, CHANNEL_ID, "agent-1",
                MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of());
        when(delegate.dispatch(dispatch)).thenReturn(expectedResult);

        DispatchResult result = passThrough.dispatch(dispatch);

        assertThat(result).isEqualTo(expectedResult);
        verify(delegate).dispatch(dispatch);
        verifyNoInteractions(clusterManager);
        verifyNoInteractions(proxyClient);
    }

    @Test
    void fallsBackToLocalOnProxyFailure() {
        var remoteNode = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(CHANNEL_ID)).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        when(proxyClient.dispatch(remoteNode, dispatch))
                .thenThrow(new RuntimeException("Connection refused"));
        var expectedResult = new DispatchResult(1L, CHANNEL_ID, "agent-1",
                MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of());
        when(delegate.dispatch(dispatch)).thenReturn(expectedResult);

        DispatchResult result = decorator.dispatch(dispatch);

        assertThat(result).isEqualTo(expectedResult);
        verify(proxyClient).dispatch(remoteNode, dispatch);
        verify(delegate).dispatch(dispatch);
    }

    @Test
    void proxiedWritesDoNotTrackFrequency() {
        WriteFrequencyTracker tracker          = mock(WriteFrequencyTracker.class);
        var                   trackedDecorator = new WriteRoutingDecorator(delegate, clusterManager, proxyClient, true, tracker);
        var                   remoteNode       = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(CHANNEL_ID)).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                                      .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        when(proxyClient.dispatch(remoteNode, dispatch)).thenReturn(
                new DispatchResult(1L, CHANNEL_ID, "agent-1", MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of()));

        trackedDecorator.dispatch(dispatch);

        verifyNoInteractions(tracker);
    }

    @Test
    void localWritesTrackFrequency() {
        WriteFrequencyTracker tracker          = mock(WriteFrequencyTracker.class);
        var                   trackedDecorator = new WriteRoutingDecorator(delegate, clusterManager, proxyClient, true, tracker);
        var                   localNode        = new NodeInfo("node-1", "node-1:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(CHANNEL_ID)).thenReturn(localNode);
        when(clusterManager.isLocal(localNode)).thenReturn(true);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                                      .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        when(delegate.dispatch(dispatch)).thenReturn(
                new DispatchResult(1L, CHANNEL_ID, "agent-1", MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of()));

        trackedDecorator.dispatch(dispatch);

        verify(tracker).recordWrite(CHANNEL_ID);
    }

    @Test
    void fallbackToLocalTracksFrequency() {
        WriteFrequencyTracker tracker          = mock(WriteFrequencyTracker.class);
        var                   trackedDecorator = new WriteRoutingDecorator(delegate, clusterManager, proxyClient, true, tracker);
        var                   remoteNode       = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(CHANNEL_ID)).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        var dispatch = MessageDispatch.builder().channelId(CHANNEL_ID)
                                      .sender("agent-1").type(MessageType.STATUS).content("test").actorType(ActorType.AGENT).build();
        when(proxyClient.dispatch(remoteNode, dispatch)).thenThrow(new RuntimeException("timeout"));
        when(delegate.dispatch(dispatch)).thenReturn(
                new DispatchResult(1L, CHANNEL_ID, "agent-1", MessageType.STATUS, null, null, List.of(), null, null, null, null, 0, null, List.of()));

        trackedDecorator.dispatch(dispatch);

        verify(tracker).recordWrite(CHANNEL_ID);
    }

}
