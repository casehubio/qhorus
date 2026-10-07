package io.casehub.qhorus.cache;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "casehub.qhorus.cache")
public interface CacheConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("shallow")
    String mode();

    @WithDefault("1000")
    int maxChannels();

    @WithDefault("200")
    int maxMessagesPerChannel();

    @WithDefault("1000")
    int fullSyncBatchSize();

    @WithDefault("5s")
    Duration fullSyncInterval();
}
