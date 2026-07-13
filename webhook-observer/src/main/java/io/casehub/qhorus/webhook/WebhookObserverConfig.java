package io.casehub.qhorus.webhook;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.qhorus.webhook")
public interface WebhookObserverConfig {

    @WithDefault("/qhorus/webhooks")
    String path();

    @WithDefault("5000")
    int timeoutMs();
}
