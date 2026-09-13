package io.casehub.qhorus.runtime.gateway;

import io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster;

public class NoOpChannelActivityBroadcaster implements ChannelActivityBroadcaster {
    @Override
    public void broadcast(ChannelActivityEvent event) {
        // no-op
    }
}
