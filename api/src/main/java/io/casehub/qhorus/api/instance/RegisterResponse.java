package io.casehub.qhorus.api.instance;

import java.util.List;

public record RegisterResponse(
        String instanceId,
        List<String> activeChannelNames,
        List<InstanceInfo> onlineInstances) {}
