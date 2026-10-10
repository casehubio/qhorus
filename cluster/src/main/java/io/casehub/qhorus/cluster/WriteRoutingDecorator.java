package io.casehub.qhorus.cluster;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import jakarta.enterprise.event.Event;

public class WriteRoutingDecorator implements MessageDispatcher {

    private static final org.jboss.logging.Logger LOG =
            org.jboss.logging.Logger.getLogger(WriteRoutingDecorator.class);

    private final MessageDispatcher delegate;
    private final ClusterManager clusterManager;
    private final WriteProxyClient proxyClient;
    private final boolean routingEnabled;
    private final WriteFrequencyTracker tracker;
    private final Event<ProxyFallbackEvent> fallbackEvent;
    private final String proxyFallback;
    private final String localNodeId;

    public WriteRoutingDecorator(MessageDispatcher delegate,
                                 ClusterManager clusterManager,
                                 WriteProxyClient proxyClient,
                                 boolean routingEnabled,
                                 WriteFrequencyTracker tracker,
                                 Event<ProxyFallbackEvent> fallbackEvent,
                                 String proxyFallback,
                                 String localNodeId) {
        this.delegate = delegate;
        this.clusterManager = clusterManager;
        this.proxyClient = proxyClient;
        this.routingEnabled = routingEnabled;
        this.tracker = tracker;
        this.fallbackEvent = fallbackEvent;
        this.proxyFallback = proxyFallback;
        this.localNodeId = localNodeId;
    }

    public WriteRoutingDecorator(MessageDispatcher delegate,
                                 ClusterManager clusterManager,
                                 WriteProxyClient proxyClient,
                                 boolean routingEnabled,
                                 WriteFrequencyTracker tracker) {
        this(delegate, clusterManager, proxyClient, routingEnabled, tracker, null, "local", null);
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
            throw new QuorumViolationException(
                    "This node is in a minority partition and cannot serve writes");
        }
        NodeInfo owner = clusterManager.owner(dispatch.channelId());
        if (clusterManager.isLocal(owner)) {
            DispatchResult result = delegate.dispatch(dispatch);
            if (tracker != null) {
                tracker.recordWrite(dispatch.channelId());
            }
            return result;
        }
        try {
            return proxyClient.dispatch(owner, dispatch);
        } catch (ProxyTimeoutException e) {
            LOG.warnf("Proxy timeout to %s: %s", owner.nodeId(), e.getMessage());
            return fallbackToLocal(dispatch, owner, e);
        } catch (ProxyAuthException e) {
            LOG.errorf("Proxy auth rejected by %s (HTTP %d) — check internal-secret config",
                       owner.nodeId(), e.statusCode());
            return fallbackToLocal(dispatch, owner, e);
        } catch (Exception e) {
            LOG.warnf("Proxy to %s failed: %s", owner.nodeId(), e.getMessage());
            return fallbackToLocal(dispatch, owner, e);
        }
    }

    private DispatchResult fallbackToLocal(MessageDispatch dispatch, NodeInfo owner, Exception e) {
        var event = new ProxyFallbackEvent(
                dispatch.channelId(), owner.nodeId(), localNodeId,
                dispatch.sender(), dispatch.type(), e.getMessage());
        if (fallbackEvent != null) {
            fallbackEvent.fireAsync(event);
        }
        if ("fail".equals(proxyFallback)) {
            throw new ProxyDispatchException(event, e);
        }
        DispatchResult result = delegate.dispatch(dispatch);
        if (tracker != null) {
            tracker.recordWrite(dispatch.channelId());
        }
        return result;
    }

}
