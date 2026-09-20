package io.casehub.qhorus.api.audit;

public record ToolTelemetry(
        int count,
        long avgDurationMs,
        long totalTokens) {
}
