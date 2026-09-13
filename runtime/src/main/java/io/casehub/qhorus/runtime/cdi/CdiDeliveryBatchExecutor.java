package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.DeliveryCursorStore;
import io.casehub.qhorus.runtime.config.DeliveryConfig;
import io.casehub.qhorus.runtime.config.QhorusTracingConfig;
import io.casehub.qhorus.runtime.gateway.DeliveryBatchExecutor;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CdiDeliveryBatchExecutor extends DeliveryBatchExecutor {

    @Inject
    public CdiDeliveryBatchExecutor(CrossTenantMessageStore messageStore,
                                    CrossTenantChannelStore channelStore,
                                    DeliveryCursorStore cursorStore,
                                    DeliveryConfig config,
                                    ChannelMembershipStore channelMembershipStore,
                                    Instance<Tracer> tracerInstance,
                                    QhorusTracingConfig tracingConfig) {
        super(messageStore, channelStore, cursorStore, config, channelMembershipStore,
                tracerInstance.isResolvable() ? tracerInstance::get : null, tracingConfig);
    }

    CdiDeliveryBatchExecutor() {}
}
