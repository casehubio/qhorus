package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;

@Input("ChannelFilterInput")
public record ChannelFilterInput(
        String keyword,
        String namePrefix,
        ChannelSemantic semantic,
        Boolean paused,
        UUID spaceId) {}
