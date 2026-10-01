package io.casehub.qhorus.compliance.core.format;

import io.casehub.qhorus.api.compliance.report.ReportFormat;

import java.util.List;

public class ReportRenderingService {

    private final List<ReportRenderer> renderers;

    public ReportRenderingService(List<ReportRenderer> renderers) {
        this.renderers = renderers;
    }

    public byte[] render(Object report, ReportFormat format) {
        return renderers.stream()
                .filter(r -> r.supports(format))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No renderer available for format " + format))
                .render(report);
    }
}
