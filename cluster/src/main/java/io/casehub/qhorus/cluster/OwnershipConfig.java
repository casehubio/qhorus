package io.casehub.qhorus.cluster;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.qhorus.relay.ownership")
public interface OwnershipConfig {

    @WithDefault("300")
    int windowSeconds();

    @WithDefault("10")
    int bucketCount();

    @WithDefault("10")
    int evaluationIntervalSeconds();

    @WithDefault("2.0")
    double hysteresisRatio();

    @WithDefault("5")
    int minClaimWrites();
}
