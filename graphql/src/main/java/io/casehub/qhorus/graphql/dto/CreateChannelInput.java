package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;

@Input("CreateChannelInput")
public record CreateChannelInput(
        String name,
        String description,
        ChannelSemantic semantic,
        List<String> barrierContributors,
        List<String> allowedWriters,
        List<String> adminInstances,
        Integer rateLimitPerChannel,
        Integer rateLimitPerInstance,
        UUID spaceId,
        List<String> protocols) {}
