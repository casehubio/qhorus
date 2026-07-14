package io.casehub.qhorus.api.gateway;

import java.util.UUID;

/**
 * Fired by {@link io.casehub.qhorus.runtime.gateway.ChannelGateway} after all backends
 * have been closed for a channel. Enables observers (e.g. webhook registry, external
 * integrations) to clean up channel-specific state without implementing ChannelBackend.
 *
 * <p>Mirrors the existing {@link ChannelInitialisedEvent} pattern.
 *
 * <p>Refs #345.
 */
public record ChannelClosedEvent(UUID channelId, String channelName) {}
