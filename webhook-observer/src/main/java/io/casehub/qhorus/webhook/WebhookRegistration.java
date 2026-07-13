package io.casehub.qhorus.webhook;

import java.util.Map;
import java.util.UUID;

public record WebhookRegistration(
        UUID id,
        UUID channelId,
        String url,
        String secret,
        Map<String, String> headers) {

    public WebhookRegistration {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL must not be blank");
        }
        if (headers == null) {
            headers = Map.of();
        }
    }
}
