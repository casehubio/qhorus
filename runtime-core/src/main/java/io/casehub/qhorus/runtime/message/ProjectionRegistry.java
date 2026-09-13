package io.casehub.qhorus.runtime.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.casehub.qhorus.api.spi.RenderableProjection;

public class ProjectionRegistry {

    private final Map<String, RenderableProjection<?>> registry;

    public ProjectionRegistry(final List<? extends RenderableProjection<?>> projections) {
        this(buildMap(projections));
    }

    private ProjectionRegistry(final Map<String, RenderableProjection<?>> registry) {
        this.registry = registry;
    }

    private static Map<String, RenderableProjection<?>> buildMap(
            final Iterable<? extends RenderableProjection<?>> projections) {
        final Map<String, RenderableProjection<?>> map = new HashMap<>();
        for (final RenderableProjection<?> p : projections) {
            final String name = p.projectionName();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                        p.getClass().getName() + ".projectionName() returned null or blank — "
                        + "each RenderableProjection must return a non-null, non-empty name");
            }
            if (map.put(name, p) != null) {
                throw new IllegalStateException(
                        "Duplicate RenderableProjection name '" + name + "' — "
                        + "each projection must have a unique projectionName()");
            }
        }
        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    public <S> RenderableProjection<S> get(final String name) {
        final RenderableProjection<?> p = registry.get(name);
        if (p == null) {
            throw new IllegalArgumentException(
                    "No projection registered with name '" + name + "'. Available: "
                    + registry.keySet());
        }
        return (RenderableProjection<S>) p;
    }

    public Set<String> registeredNames() {
        return registry.keySet();
    }
}
