package io.casehub.qhorus.api.compliance.report;

public record AttributionEdge(
        String from,
        String to,
        String type,
        Long elapsedMs) {
}
