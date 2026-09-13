package io.casehub.qhorus.runtime.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.qhorus.presence")
public interface PresenceConfig {

    @WithDefault("PT2M")
    Duration awayTimeout();

    @WithDefault("PT10M")
    Duration offlineTimeout();

    @WithDefault("PT30S")
    Duration heartbeatInterval();
}
