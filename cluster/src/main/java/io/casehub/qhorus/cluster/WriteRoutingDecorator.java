package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;

public class WriteRoutingDecorator implements MessageDispatcher {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WriteRoutingDecorator.class);

    private final MessageDispatcher delegate;
    private final ClusterManager clusterManager;
    private final WriteProxyClient proxyClient;
    private final boolean routingEnabled;

    public WriteRoutingDecorator(MessageDispatcher delegate,
                                 ClusterManager clusterManager,
                                 WriteProxyClient proxyClient,
                                 boolean routingEnabled) {
        this.delegate = delegate;
        this.clusterManager = clusterManager;
        this.proxyClient = proxyClient;
        this.routingEnabled = routingEnabled;
    }

    @Override
    public DispatchResult dispatch(MessageDispatch dispatch) {
        if (!routingEnabled) {
            return delegate.dispatch(dispatch);
        }
        if (!clusterManager.canServeWrites()) {
            throw new QuorumViolationException("This node is in a minority partition and cannot serve writes");
        }
        NodeInfo owner = clusterManager.owner(dispatch.channelId());
        if (clusterManager.isLocal(owner)) {
            return delegate.dispatch(dispatch);
        }
        try {
            return proxyClient.dispatch(owner, dispatch);
        } catch (Exception e) {
            LOG.warnf("Proxy to %s failed, falling back to local dispatch: %s",
                    owner.nodeId(), e.getMessage());
            return delegate.dispatch(dispatch);
        }
    }
}
