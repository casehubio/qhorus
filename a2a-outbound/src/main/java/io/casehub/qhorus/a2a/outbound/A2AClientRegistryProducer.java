package io.casehub.qhorus.a2a.outbound;

import io.casehub.a2a.client.A2AClientRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;

class A2AClientRegistryProducer {

    @Produces
    @ApplicationScoped
    A2AClientRegistry registry() {
        return new A2AClientRegistry();
    }

    void shutdown(@Disposes A2AClientRegistry registry) {
        registry.shutdown();
    }
}
