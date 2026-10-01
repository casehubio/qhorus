package io.casehub.qhorus.connector.backend.core;

import io.casehub.qhorus.api.gateway.ChannelRef;

public final class OutboundTitle {

    private OutboundTitle() {}

    public static String forConnector(final String outboundConnectorId, final ChannelRef channel) {
        if ("email".equals(outboundConnectorId)) {
            return "Re: " + channel.name();
        }
        return null;
    }
}
