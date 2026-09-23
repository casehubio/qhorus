package io.casehub.qhorus.webhook;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.webhook.core.WebhookRegistryCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class WebhookBeans {

    @Produces
    @ApplicationScoped
    public WebhookRegistryCore webhookRegistryCore(WebhookRegistry registry,
                                                    CurrentPrincipal currentPrincipal) {
        return new WebhookRegistryCore(registry, currentPrincipal);
    }
}
