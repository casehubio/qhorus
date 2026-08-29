package io.casehub.qhorus.api.a2a;

import java.util.UUID;

@FunctionalInterface
public interface PushNotificationRegistrar {
    void onConfigCreated(UUID channelId, String taskId);
}
