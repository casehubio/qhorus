package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;

import java.util.UUID;

public class WriteProxyClient {

    public DispatchResult dispatch(NodeInfo target, MessageDispatch dispatch) {
        throw new UnsupportedOperationException("HTTP proxy not wired — requires InternalMeshClient");
    }

    public Channel createChannel(NodeInfo target, ChannelCreateRequest request) {
        throw new UnsupportedOperationException("HTTP proxy not wired — requires InternalMeshClient");
    }

    public long deleteChannel(NodeInfo target, UUID channelId, boolean force) {
        throw new UnsupportedOperationException("HTTP proxy not wired — requires InternalMeshClient");
    }

    public Channel pauseChannel(NodeInfo target, UUID channelId) {
        throw new UnsupportedOperationException("HTTP proxy not wired — requires InternalMeshClient");
    }

    public Channel resumeChannel(NodeInfo target, UUID channelId) {
        throw new UnsupportedOperationException("HTTP proxy not wired — requires InternalMeshClient");
    }
}
