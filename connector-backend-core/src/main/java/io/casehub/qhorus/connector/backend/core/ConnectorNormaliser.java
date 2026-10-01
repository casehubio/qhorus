package io.casehub.qhorus.connector.backend.core;

import io.casehub.qhorus.api.gateway.InboundNormaliser;

public interface ConnectorNormaliser extends InboundNormaliser {
    String connectorId();
}
