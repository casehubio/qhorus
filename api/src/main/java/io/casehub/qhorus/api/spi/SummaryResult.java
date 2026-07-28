package io.casehub.qhorus.api.spi;

import java.util.Map;

public record SummaryResult(String text, Map<String, String> annotations) {
    public SummaryResult {
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }

    public static SummaryResult ofText(String text) {
        return new SummaryResult(text, Map.of());
    }
}
