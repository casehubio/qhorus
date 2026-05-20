package io.casehub.qhorus.service;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Activates reactive service alternatives for integration testing.
 *
 * <p>
 * NOTE: Reactive services call {@code Panache.withTransaction()} which requires a
 * native reactive datasource driver. H2 has no reactive driver — tests using this
 * profile must be {@code @Disabled} until a PostgreSQL Dev Services or Docker
 * environment is available.
 */
public class ReactiveTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // casehub.qhorus.reactive.enabled=true activates reactive beans via @IfBuildProperty
        // at augmentation time for the restarted Quarkus context.
        // quarkus.arc.selected-alternatives was removed — reactive service beans no longer
        // carry @Alternative (that was part of the old gating pattern, now superseded by
        // @IfBuildProperty with casehub.qhorus.reactive.enabled).
        return Map.of("casehub.qhorus.reactive.enabled", "true");
    }
}
