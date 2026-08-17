package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("Channel")
public record ChannelType(
        UUID id,
        String name,
        String description,
        ChannelSemantic semantic,
        List<String> allowedWriters,
        List<String> adminInstances,
        boolean paused,
        UUID spaceId,
        List<String> protocols,
        String tenancyId,
        Instant createdAt,
        Instant lastActivityAt) {

    public static ChannelType from(Channel channel) {
        return new ChannelType(
                channel.id(),
                channel.name(),
                channel.description(),
                channel.semantic(),
                channel.allowedWriters(),
                channel.adminInstances(),
                channel.paused(),
                channel.spaceId(),
                channel.protocols(),
                channel.tenancyId(),
                channel.createdAt(),
                channel.lastActivityAt());
    }
}
