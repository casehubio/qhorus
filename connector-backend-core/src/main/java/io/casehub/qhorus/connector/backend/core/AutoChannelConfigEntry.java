package io.casehub.qhorus.connector.backend.core;

public record AutoChannelConfigEntry(boolean enabled, String outboundConnectorId,
                                      String channelNamePattern, String semantic) {}
