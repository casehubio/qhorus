package io.casehub.qhorus.cluster;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.qhorus.cluster")
public interface ClusterConfig {

    @WithDefault("false")
    boolean enabled();

    Optional<List<String>> peers();

    Optional<String> nodeId();

    @WithDefault("128")
    int virtualNodes();

    @WithDefault("3s")
    Duration heartbeatInterval();

    @WithDefault("2")
    int heartbeatMissThreshold();

    @WithDefault("30s")
    Duration drainTimeout();

    @WithDefault("true")
    boolean quorumEnforced();

    @WithDefault("10s")
    Duration proxyTimeout();
}
