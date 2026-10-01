package io.casehub.qhorus.notification.bridge;

import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.CommitmentExpiredEvent;
import io.casehub.qhorus.notification.bridge.core.CommitmentEventNotifierCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class CommitmentEventNotifier {

    @Inject
    CommitmentEventNotifierCore core;

    void onDeclined(@ObservesAsync CommitmentDeclinedEvent event) {
        core.onDeclined(event);
    }

    void onExpired(@ObservesAsync CommitmentExpiredEvent event) {
        core.onExpired(event);
    }
}
