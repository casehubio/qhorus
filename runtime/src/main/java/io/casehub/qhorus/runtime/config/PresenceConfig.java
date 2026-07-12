package io.casehub.qhorus.runtime.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the presence subsystem.
 */
@ConfigMapping(prefix = "casehub.qhorus.presence")
public interface PresenceConfig {

    /**
     * Duration of heartbeat absence before status degrades to AWAY.
     */
    @WithDefault("PT2M")
    Duration awayTimeout();

    /**
     * Duration of heartbeat absence before the cache entry expires (member becomes OFFLINE).
     * Must be greater than awayTimeout.
     */
    @WithDefault("PT10M")
    Duration offlineTimeout();

    /**
     * Suggested heartbeat interval returned to clients so they know how often to call set_presence.
     */
    @WithDefault("PT30S")
    Duration heartbeatInterval();
}
