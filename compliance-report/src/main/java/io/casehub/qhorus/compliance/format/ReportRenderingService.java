package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.ReportFormat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ReportRenderingService {

    private final List<ReportRenderer> renderers;

    @Inject
    public ReportRenderingService(@Any Instance<ReportRenderer> renderers) {
        this.renderers = renderers.stream().toList();
    }

    ReportRenderingService(List<ReportRenderer> renderers) {
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
