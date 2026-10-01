package io.casehub.qhorus.push;

import io.casehub.qhorus.api.event.ChannelMutationEvent;
import io.casehub.qhorus.push.core.QhorusWebSocketBroadcasterCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class QhorusWebSocketBroadcaster {

    @Inject
    QhorusWebSocketBroadcasterCore core;

    void onMutation(@Observes ChannelMutationEvent event) {
        core.onMutation(event);
    }
}
