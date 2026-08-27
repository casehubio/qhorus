package io.casehub.qhorus.compliance.model;

public record AttributionEdge(
        String from,
        String to,
        String type,
        Long elapsedMs) {
}
