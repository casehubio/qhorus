package io.casehub.qhorus.a2a.push;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.qhorus.a2a.push")
public interface PushConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("PT24H")
    Duration ttlThreshold();

    @WithDefault("PT5M")
    Duration cleanupInterval();

    @WithDefault("5")
    int maxUrlFailures();

    @WithDefault("5000")
    int httpTimeoutMs();
}
