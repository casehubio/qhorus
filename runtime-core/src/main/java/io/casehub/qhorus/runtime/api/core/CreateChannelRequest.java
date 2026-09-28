package io.casehub.qhorus.runtime.api.core;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateChannelRequest(
        String name,
        String description,
        String semantic,
        List<String> barrierContributors,
        List<String> allowedWriters,
        List<String> adminInstances,
        List<String> reviewerInstances,
        Set<String> allowedTypes,
        Set<String> deniedTypes,
        List<String> protocols,
        List<String> protocolParticipants,
        UUID spaceId,
        Boolean trackDelivery,
        Integer rateLimitPerChannel,
        Integer rateLimitPerInstance) {}
