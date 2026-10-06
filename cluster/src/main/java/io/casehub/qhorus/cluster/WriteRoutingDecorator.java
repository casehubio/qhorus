package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;

public class WriteRoutingDecorator implements MessageDispatcher {

    private final MessageDispatcher delegate;
    private final ClusterManager clusterManager;
    private final WriteProxyClient proxyClient;

    public WriteRoutingDecorator(MessageDispatcher delegate,
                                 ClusterManager clusterManager,
                                 WriteProxyClient proxyClient) {
        this.delegate = delegate;
        this.clusterManager = clusterManager;
        this.proxyClient = proxyClient;
    }

    @Override
    public DispatchResult dispatch(MessageDispatch dispatch) {
        if (!clusterManager.canServeWrites()) {
            throw new QuorumViolationException("This node is in a minority partition and cannot serve writes");
        }
        NodeInfo owner = clusterManager.owner(dispatch.channelId());
        if (clusterManager.isLocal(owner)) {
            return delegate.dispatch(dispatch);
        }
        return proxyClient.dispatch(owner, dispatch);
    }
}
