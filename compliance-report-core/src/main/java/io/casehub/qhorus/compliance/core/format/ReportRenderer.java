package io.casehub.qhorus.compliance.core.format;

import io.casehub.qhorus.api.compliance.report.ReportFormat;

public interface ReportRenderer {
    String contentType();
    byte[] render(Object report);
    boolean supports(ReportFormat format);
}
