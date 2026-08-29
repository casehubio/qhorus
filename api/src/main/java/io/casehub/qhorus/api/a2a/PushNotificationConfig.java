package io.casehub.qhorus.api.a2a;

import java.time.Instant;
import java.util.UUID;

public record PushNotificationConfig(
    UUID id,
    String taskId,
    UUID channelId,
    String url,
    String token,
    String authScheme,
    String authCredentialsRef,
    String tenancyId,
    Instant createdAt,
    Instant lastPushedAt
) {}
