package io.casehub.qhorus.webhook;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebhookRegistration(
        UUID id,
        UUID channelId,
        String tenancyId,
        String url,
        String secretRef,
        Map<String, String> headers,
        Instant createdAt) {

    public WebhookRegistration {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be blank");
        }
        if (headers == null) {
            headers = Map.of();
        }
    }
}
