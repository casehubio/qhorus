package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushNotificationConfigStore {

    void put(PushNotificationConfig config);

    Optional<PushNotificationConfig> findById(UUID id);

    List<PushNotificationConfig> findByTaskId(String taskId);

    void delete(UUID id);

    void deleteByTaskId(String taskId);
}
