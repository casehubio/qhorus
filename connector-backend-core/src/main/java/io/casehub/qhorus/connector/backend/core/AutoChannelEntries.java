package io.casehub.qhorus.connector.backend.core;

import java.util.Map;
import java.util.Optional;

public interface AutoChannelEntries {

    Map<String, ? extends Entry> entries();

    interface Entry {
        boolean enabled();
        Optional<String> outboundConnectorId();
        Optional<String> channelNamePattern();
        Optional<String> semantic();
    }
}
