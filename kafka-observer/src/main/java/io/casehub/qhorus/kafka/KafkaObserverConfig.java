package io.casehub.qhorus.kafka;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;
import java.util.Set;

@ConfigMapping(prefix = "casehub.qhorus.kafka")
public interface KafkaObserverConfig {

    @WithDefault("qhorus-messages")
    String topic();

    Optional<Set<String>> channels();
}
