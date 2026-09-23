package io.casehub.qhorus.a2a.outbound;

import io.casehub.qhorus.a2a.outbound.core.ExternalAgentBindingCore;
import io.casehub.qhorus.api.event.BindingVerificationRequestedEvent;
import io.casehub.qhorus.api.spi.AgentCardSigner;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class A2AOutboundBeans {

    @Produces
    @ApplicationScoped
    public ExternalAgentBindingCore externalAgentBindingCore(
            ExternalAgentBindingStore store,
            Event<BindingVerificationRequestedEvent> verificationEvent,
            Instance<AgentCardSigner> agentCardSigner) {
        return new ExternalAgentBindingCore(
                store,
                e -> verificationEvent.fireAsync(e),
                agentCardSigner.isResolvable());
    }
}
