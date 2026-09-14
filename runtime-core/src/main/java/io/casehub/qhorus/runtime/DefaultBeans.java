package io.casehub.qhorus.runtime;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.spi.InstanceActorIdProvider;
import io.casehub.qhorus.api.spi.ObligorTrustPolicy;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.casehub.qhorus.runtime.channel.NoOpSummaryUpdateHook;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.gateway.DefaultInboundNormaliser;
import io.casehub.qhorus.runtime.gateway.NoOpChannelActivityBroadcaster;
import io.casehub.qhorus.runtime.ledger.DefaultInstanceActorIdProvider;
import io.casehub.qhorus.runtime.message.DefaultObligorTrustPolicy;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class DefaultBeans {

    @Produces
    @DefaultBean
    @ApplicationScoped
    public InboundNormaliser inboundNormaliser() {
        return new DefaultInboundNormaliser();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public SummaryUpdateHook summaryUpdateHook() {
        return new NoOpSummaryUpdateHook();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public InstanceActorIdProvider instanceActorIdProvider() {
        return new DefaultInstanceActorIdProvider();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public ChannelActivityBroadcaster channelActivityBroadcaster() {
        return new NoOpChannelActivityBroadcaster();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public ObligorTrustPolicy obligorTrustPolicy(
            QhorusConfig config,
            Instance<TrustGateService> trustGateServiceInstance) {
        return new DefaultObligorTrustPolicy(
                config.commitment().minObligorTrust(),
                trustGateServiceInstance.isResolvable() ? trustGateServiceInstance.get() : null);
    }
}
