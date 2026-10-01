package io.casehub.qhorus.connector.backend.core;

import java.util.Set;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundMessage;

public final class ConnectorKeyStrategy {

    private static final Set<String> SENDER_KEYED = Set.of(
            InboundConnectorIds.TWILIO_SMS,
            InboundConnectorIds.WHATSAPP,
            InboundConnectorIds.EMAIL
    );

    private ConnectorKeyStrategy() {}

    public static String deriveKey(final InboundMessage msg) {
        if (SENDER_KEYED.contains(msg.connectorId())) {
            return msg.externalSenderId();
        }
        return msg.externalChannelRef();
    }
}
