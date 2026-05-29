package io.casehub.qhorus.service;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Activates the reactive service stack with PostgreSQL DevServices.
 *
 * <p>Requires Podman ≥ 4 GB (or Docker). Quarkus DevServices starts
 * {@code postgres:17-alpine} automatically.
 *
 * <p>{@code casehub.qhorus.reactive.enabled=true} activates reactive beans via
 * {@code @IfBuildProperty} at augmentation time for the restarted context.
 * This property must NOT appear in {@code application.properties} — it is BUILD_TIME
 * only and would cause {@code SRCFG00050} at runtime validation.
 */
public class ReactiveTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "reactive-pg";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("casehub.qhorus.reactive.enabled", "true");
    }
}
