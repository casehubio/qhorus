package io.casehub.qhorus.webhook.core;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.webhook.WebhookRegistration;
import io.casehub.qhorus.webhook.WebhookRegistry;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WebhookRegistryCore {

    private final WebhookRegistry registry;
    private final CurrentPrincipal currentPrincipal;

    public WebhookRegistryCore(WebhookRegistry registry, CurrentPrincipal currentPrincipal) {
        this.registry = registry;
        this.currentPrincipal = currentPrincipal;
    }

    public WebhookRegistration register(RegisterRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        return registry.register(
                request.channelId(), currentPrincipal.tenancyId(),
                request.url(), request.secretRef(),
                request.headers() != null ? request.headers() : Map.of());
    }

    public Optional<Void> deregister(UUID id) {
        return registry.deregister(id) ? Optional.of(null) : Optional.empty();
    }

    public Collection<WebhookRegistration> list(UUID channelId) {
        if (channelId != null) {
            return registry.findByChannelId(channelId);
        }
        return registry.listAll(currentPrincipal.tenancyId());
    }
}
