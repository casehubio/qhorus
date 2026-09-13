package io.casehub.qhorus.runtime.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.qhorus.delivery")
public interface DeliveryConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("100")
    int batchSize();

    @WithDefault("10")
    int maxConsecutiveFailures();

    @WithDefault("30s")
    String reconciliationInterval();

    @WithDefault("100")
    int maxParticipantRetriesPerCycle();

    @WithDefault("3")
    int maxParticipantConsecutiveFailures();
}
