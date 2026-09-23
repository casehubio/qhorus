package io.casehub.qhorus.webhook.core;

import java.util.Map;
import java.util.UUID;

public record RegisterRequest(UUID channelId, String url, String secretRef, Map<String, String> headers) {
}
