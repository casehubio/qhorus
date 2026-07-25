package io.casehub.qhorus.api.gateway;

import java.util.List;
import java.util.UUID;

public interface BackendRegistry {

    void registerBackend(UUID channelId, ChannelBackend backend, String backendType);

    void deregisterBackend(UUID channelId, String backendId);

    List<BackendRegistration> listBackends(UUID channelId);
}
