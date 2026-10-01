package io.casehub.qhorus.connector.backend.core;

import java.util.Set;

import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;

public record AutoChannelSpec(
        String channelName,
        String description,
        ChannelSemantic semantic,
        Set<MessageType> allowedTypes,
        Set<MessageType> deniedTypes,
        String outboundConnectorId,
        String outboundDestination
) {}
