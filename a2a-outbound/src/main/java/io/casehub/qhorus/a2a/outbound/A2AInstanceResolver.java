package io.casehub.qhorus.a2a.outbound;

import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class A2AInstanceResolver {

    private final ExternalAgentBindingStore store;

    @Inject
    public A2AInstanceResolver(ExternalAgentBindingStore store) {
        this.store = store;
    }

    public Optional<ExternalAgentBinding> resolve(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        return store.findByInstanceId(target);
    }

    public boolean isExternalAgent(String instanceId) {
        return resolve(instanceId).isPresent();
    }
}
