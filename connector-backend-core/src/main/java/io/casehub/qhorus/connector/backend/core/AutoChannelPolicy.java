package io.casehub.qhorus.connector.backend.core;

import java.util.Optional;

import io.casehub.connectors.InboundMessage;

public interface AutoChannelPolicy {
    Optional<AutoChannelSpec> onFirstContact(InboundMessage msg, String lookupKey);
}
