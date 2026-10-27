package io.casehub.qhorus.api.gateway;

import java.util.List;
import java.util.UUID;

public record ChannelProtocolsChangedEvent(
        UUID channelId,
        String channelName,
        List<String> addedProtocols,
        List<String> removedProtocols) {}
