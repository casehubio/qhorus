package io.casehub.qhorus.runtime.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.qhorus.tracing")
public interface QhorusTracingConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("true")
    boolean dispatch();

    @WithDefault("true")
    boolean commitments();

    @WithDefault("true")
    boolean fanOut();

    @WithDefault("true")
    boolean ledgerWrite();

    @WithDefault("true")
    boolean delivery();
}
