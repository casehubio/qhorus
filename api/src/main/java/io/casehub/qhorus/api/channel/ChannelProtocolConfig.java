package io.casehub.qhorus.api.channel;

import java.util.List;

public record ChannelProtocolConfig(
        List<String> protocols,
        List<String> protocolParticipants) {}
