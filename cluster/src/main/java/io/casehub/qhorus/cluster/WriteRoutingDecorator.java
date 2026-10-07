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
    private final WriteFrequencyTracker tracker;

    public WriteRoutingDecorator(MessageDispatcher delegate,
                                 ClusterManager clusterManager,
                                 WriteProxyClient proxyClient,
                                 boolean routingEnabled,
                                 WriteFrequencyTracker tracker) {
        this.delegate = delegate;
        this.clusterManager = clusterManager;
        this.proxyClient = proxyClient;
        this.routingEnabled = routingEnabled;
        this.tracker = tracker;
    }

    public WriteRoutingDecorator(MessageDispatcher delegate,
                                 ClusterManager clusterManager,
                                 WriteProxyClient proxyClient,
                                 boolean routingEnabled) {
        this(delegate, clusterManager, proxyClient, routingEnabled, null);
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
        DispatchResult result;
        if (clusterManager.isLocal(owner)) {
            result = delegate.dispatch(dispatch);
        } else {
            try {
                result = proxyClient.dispatch(owner, dispatch);
            } catch (Exception e) {
                LOG.warnf("Proxy to %s failed, falling back to local dispatch: %s",
                        owner.nodeId(), e.getMessage());
                result = delegate.dispatch(dispatch);
            }
        }
        if (tracker != null) {
            tracker.recordWrite(dispatch.channelId());
        }
        return result;
    }
}
