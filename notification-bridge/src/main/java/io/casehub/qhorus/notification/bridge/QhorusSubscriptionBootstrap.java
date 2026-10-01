package io.casehub.qhorus.notification.bridge;

import io.casehub.qhorus.notification.bridge.core.QhorusSubscriptionBootstrapCore;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class QhorusSubscriptionBootstrap {

    @Inject
    QhorusSubscriptionBootstrapCore core;

    void onStartup(@Observes StartupEvent event) {
        core.bootstrap();
    }
}
