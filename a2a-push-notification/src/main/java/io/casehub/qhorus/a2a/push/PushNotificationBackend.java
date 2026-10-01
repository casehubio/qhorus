package io.casehub.qhorus.a2a.push;

import io.casehub.qhorus.a2a.push.core.PushNotificationBackendCore;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class PushNotificationBackend {

    @Inject
    PushNotificationBackendCore core;

    void onChannelRecovery(@Observes ChannelInitialisedEvent event) {
        core.onChannelRecovery(event.channelId());
    }
}
