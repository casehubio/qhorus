package io.casehub.qhorus.api.channel;

import java.util.UUID;

public record ChannelDetail(
        UUID channelId,
        String name,
        String description,
        String semantic,
        String barrierContributors,
        long messageCount,
        String lastActivityAt,
        boolean paused,
        String allowedWriters,
        String adminInstances,
        Integer rateLimitPerChannel,
        Integer rateLimitPerInstance,
        String allowedTypes,
        String deniedTypes,
        UUID spaceId,
        String spaceName,
        ConnectorBinding connectorBinding) {

    public record ConnectorBinding(
            String inboundConnectorId,
            String externalKey,
            String outboundConnectorId,
            String outboundDestination) {}
}
