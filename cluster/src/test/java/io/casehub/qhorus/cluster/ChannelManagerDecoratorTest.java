package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelManagerDecoratorTest {

    private ChannelManager delegate;
    private ClusterManager clusterManager;
    private WriteProxyClient proxyClient;
    private ChannelManagerDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = mock(ChannelManager.class);
        clusterManager = mock(ClusterManager.class);
        proxyClient = mock(WriteProxyClient.class);
        decorator = new ChannelManagerDecorator(delegate, clusterManager, proxyClient, true);
    }

    @Test
    void createRoutesToLocalOwnerWithPreAssignedId() {
        var localNode = new NodeInfo("node-1", "node-1:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(any(UUID.class))).thenReturn(localNode);
        when(clusterManager.isLocal(localNode)).thenReturn(true);
        var req = ChannelCreateRequest.builder("test-ch").build();
        var channel = mock(Channel.class);
        when(delegate.create(any())).thenReturn(channel);

        Channel result = decorator.create(req);

        assertThat(result).isEqualTo(channel);
        verify(delegate).create(argThat(r -> r.preAssignedId() != null));
    }

    @Test
    void createProxiesToRemoteOwner() {
        var remoteNode = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(any(UUID.class))).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        var req = ChannelCreateRequest.builder("test-ch").build();
        var channel = mock(Channel.class);
        when(proxyClient.createChannel(any(), any())).thenReturn(channel);

        Channel result = decorator.create(req);

        assertThat(result).isEqualTo(channel);
        verify(proxyClient).createChannel(any(), argThat(r -> r.preAssignedId() != null));
    }

    @Test
    void deleteRoutesToOwnerByChannelId() {
        UUID channelId = UUID.randomUUID();
        var remoteNode = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.canServeWrites()).thenReturn(true);
        when(clusterManager.owner(channelId)).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        when(proxyClient.deleteChannel(remoteNode, channelId, true)).thenReturn(5L);

        long result = decorator.delete(channelId, true);

        assertThat(result).isEqualTo(5L);
        verify(proxyClient).deleteChannel(remoteNode, channelId, true);
    }

    @Test
    void pauseRoutesToOwner() {
        UUID channelId = UUID.randomUUID();
        var localNode = new NodeInfo("node-1", "node-1:8080");
        when(clusterManager.owner(channelId)).thenReturn(localNode);
        when(clusterManager.isLocal(localNode)).thenReturn(true);
        var channel = mock(Channel.class);
        when(delegate.pause(channelId)).thenReturn(channel);

        Channel result = decorator.pause(channelId);

        assertThat(result).isEqualTo(channel);
        verify(delegate).pause(channelId);
    }

    @Test
    void throwsOnQuorumViolationForCreate() {
        when(clusterManager.canServeWrites()).thenReturn(false);
        var req = ChannelCreateRequest.builder("test-ch").build();
        assertThatThrownBy(() -> decorator.create(req))
                .isInstanceOf(QuorumViolationException.class);
    }

    @Test
    void throwsOnQuorumViolationForDelete() {
        when(clusterManager.canServeWrites()).thenReturn(false);
        assertThatThrownBy(() -> decorator.delete(UUID.randomUUID(), false))
                .isInstanceOf(QuorumViolationException.class);
    }

    @Test
    void remotePauseProxiesViaProxyClient() {
        UUID channelId  = UUID.randomUUID();
        var  remoteNode = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.owner(channelId)).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        var channel = mock(Channel.class);
        when(proxyClient.pauseChannel(remoteNode, channelId)).thenReturn(channel);

        Channel result = decorator.pause(channelId);

        assertThat(result).isEqualTo(channel);
        verify(proxyClient).pauseChannel(remoteNode, channelId);
    }

    @Test
    void remoteSetAllowedWritersProxiesViaConfigEndpoint() {
        UUID channelId  = UUID.randomUUID();
        var  remoteNode = new NodeInfo("node-2", "node-2:8080");
        when(clusterManager.owner(channelId)).thenReturn(remoteNode);
        when(clusterManager.isLocal(remoteNode)).thenReturn(false);
        var channel = mock(Channel.class);
        when(proxyClient.channelConfig(any(), any(), any())).thenReturn(channel);

        Channel result = decorator.setAllowedWriters(channelId, java.util.List.of("agent-1"));

        assertThat(result).isEqualTo(channel);
        verify(proxyClient).channelConfig(any(), any(), argThat(req ->
                                                                        "setAllowedWriters".equals(req.operation())));
    }

    @Test
    void findOrCreate_rejects_in_minority_partition() {
        when(clusterManager.canServeWrites()).thenReturn(false);
        var request = ChannelCreateRequest.builder("quorum-test").build();
        assertThatThrownBy(() -> decorator.findOrCreate(request))
                .isInstanceOf(QuorumViolationException.class);
    }

    @Test
    void findOrCreate_delegates_when_quorum_present() {
        when(clusterManager.canServeWrites()).thenReturn(true);
        var request  = ChannelCreateRequest.builder("quorum-ok").build();
        var channel  = mock(Channel.class);
        var expected = new FindOrCreateResult(channel, false);
        when(delegate.findOrCreate(request)).thenReturn(expected);
        var result = decorator.findOrCreate(request);
        assertThat(result).isEqualTo(expected);
    }


}
