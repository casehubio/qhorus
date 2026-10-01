package io.casehub.qhorus.connector.backend;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

import io.casehub.connectors.InboundMessage;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.connector.backend.core.ConnectorChannelBackendCore;

@ApplicationScoped
public class ConnectorChannelBackendObserver {

    @Inject
    ConnectorChannelBackendCore core;

    void onChannelInitialised(@Observes ChannelInitialisedEvent event) {
        core.onChannelInitialised(event.channelId());
    }

    CompletionStage<Void> onInboundMessage(@ObservesAsync InboundMessage msg) {
        core.onInboundMessage(msg);
        return CompletableFuture.completedFuture(null);
    }
}
