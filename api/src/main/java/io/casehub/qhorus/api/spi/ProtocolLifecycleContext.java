package io.casehub.qhorus.api.spi;

import io.casehub.qhorus.api.message.MessageView;

import java.util.List;
import java.util.UUID;

public record ProtocolLifecycleContext(
        UUID channelId,
        String channelName,
        String protocolName,
        List<String> protocolParticipants,
        List<MessageView> messageHistory,
        boolean recovered) {

    public ProtocolLifecycleContext {
        protocolParticipants = protocolParticipants != null
                ? List.copyOf(protocolParticipants) : List.of();
        messageHistory = messageHistory != null
                ? List.copyOf(messageHistory) : List.of();
    }
}
