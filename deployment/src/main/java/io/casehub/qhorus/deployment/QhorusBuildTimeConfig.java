package io.casehub.qhorus.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for the Qhorus extension.
 *
 * <p>
 * Properties declared here are evaluated during Quarkus augmentation and are not
 * available at runtime. Changes require a rebuild.
 */
@ConfigMapping(prefix = "casehub.qhorus")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface QhorusBuildTimeConfig {

    /** Reactive service tier configuration. */
    ReactiveConfig reactive();

    interface ReactiveConfig {
        /**
         * Whether to activate the reactive service tier.
         *
         * <p>
         * Set to {@code true} in deployments that provide a reactive datasource
         * (e.g. Hibernate Reactive + reactive PostgreSQL client). JDBC-only consumers
         * must leave this unset — the default {@code false} excludes all reactive
         * beans from CDI augmentation, preventing unsatisfied-dependency failures.
         * Representative reactive beans:
         * {@link io.casehub.qhorus.runtime.mcp.ReactiveQhorusMcpTools},
         * {@link io.casehub.qhorus.runtime.api.ReactiveA2AResource}.
         *
         * <p>
         * Corresponds to {@code casehub.qhorus.reactive.enabled} in
         * {@code application.properties}.
         */
        @WithDefault("false")
        boolean enabled();
    }
}
