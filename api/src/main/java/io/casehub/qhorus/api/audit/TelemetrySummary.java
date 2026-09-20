package io.casehub.qhorus.api.audit;

import java.util.Map;

public record TelemetrySummary(
        int totalEvents,
        Map<String, ToolTelemetry> byTool,
        long totalTokens,
        long totalDurationMs) {
}
