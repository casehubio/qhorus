package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CrossTenantPushNotificationConfigStore {

    List<PushNotificationConfig> findByTaskId(String taskId);

    List<PushNotificationConfig> findByChannelId(UUID channelId);

    Set<String> activeTaskIds();

    List<PushNotificationConfig> findExpired(Instant threshold);

    void updateLastPushedAt(UUID id, Instant pushedAt);

    void delete(UUID id);

    void deleteByTaskId(String taskId);
}
