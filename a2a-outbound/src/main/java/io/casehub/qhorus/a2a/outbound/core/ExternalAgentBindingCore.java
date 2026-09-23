package io.casehub.qhorus.a2a.outbound.core;

import io.casehub.qhorus.api.event.BindingVerificationRequestedEvent;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import io.casehub.qhorus.api.instance.VerificationStatus;
import io.casehub.qhorus.api.store.ExternalAgentBindingStore;
import io.casehub.qhorus.a2a.outbound.ExternalAgentBindingRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ExternalAgentBindingCore {

    private final ExternalAgentBindingStore store;
    private final Consumer<BindingVerificationRequestedEvent> verificationEventConsumer;
    private final boolean signerAvailable;

    public ExternalAgentBindingCore(ExternalAgentBindingStore store,
                                    Consumer<BindingVerificationRequestedEvent> verificationEventConsumer,
                                    boolean signerAvailable) {
        this.store = store;
        this.verificationEventConsumer = verificationEventConsumer;
        this.signerAvailable = signerAvailable;
    }

    public ExternalAgentBinding put(String instanceId, ExternalAgentBindingRequest req) {
        if (req.endpoint() == null || req.endpoint().isBlank()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        String version = req.protocolVersion() != null ? req.protocolVersion() : "1.0";

        ExternalAgentBinding existing = store.findByInstanceId(instanceId).orElse(null);
        UUID id = existing != null ? existing.id() : UUID.randomUUID();

        ExternalAgentBinding binding = new ExternalAgentBinding(
                id, instanceId, req.endpoint(), req.authConfigKey(), version, Instant.now(),
                VerificationStatus.UNVERIFIED, null, null);
        store.put(binding);
        if (signerAvailable) {
            verificationEventConsumer.accept(new BindingVerificationRequestedEvent(binding));
        }
        return binding;
    }

    public ExternalAgentBinding get(String instanceId) {
        return store.findByInstanceId(instanceId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException());
    }

    public List<ExternalAgentBinding> list() {
        return store.findAll();
    }

    public void delete(String instanceId) {
        store.deleteByInstanceId(instanceId);
    }

    public Optional<ExternalAgentBinding> verify(String instanceId) {
        if (!signerAvailable) {
            throw new UnsupportedOperationException("Agent card signing module not configured — verification unavailable");
        }
        return store.findByInstanceId(instanceId)
                .map(binding -> {
                    verificationEventConsumer.accept(new BindingVerificationRequestedEvent(binding));
                    return binding;
                });
    }
}
