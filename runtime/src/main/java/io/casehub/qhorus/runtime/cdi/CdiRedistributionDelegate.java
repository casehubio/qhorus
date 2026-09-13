package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.capacity.RedistributionExecutedEvent;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.runtime.capacity.RedistributionDelegate;
import io.casehub.qhorus.runtime.channel.ChannelSummaryService;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.runtime.message.RoutingBridge;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Transactional
public class CdiRedistributionDelegate extends RedistributionDelegate {

    @Inject
    public CdiRedistributionDelegate(ChannelSummaryService summaryService,
                                     MessageService messageService,
                                     RoutingBridge routingBridge,
                                     CrossTenantChannelStore channelStore,
                                     MessageStore messageStore,
                                     InboundTenancyContext inboundTenancyContext,
                                     Event<RedistributionExecutedEvent> executedEvents,
                                     @ConfigProperty(name = "casehub.capacity.redistribution.redistribute-threshold",
                                             defaultValue = "0.85") double threshold) {
        super(summaryService, messageService, routingBridge, channelStore, messageStore,
                inboundTenancyContext::set, e -> executedEvents.fireAsync(e), threshold);
    }

    CdiRedistributionDelegate() {}
}
