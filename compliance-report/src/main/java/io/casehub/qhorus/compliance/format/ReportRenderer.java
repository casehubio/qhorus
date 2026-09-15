package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.api.compliance.report.ReportFormat;

public interface ReportRenderer {
    String contentType();
    byte[] render(Object report);
    boolean supports(ReportFormat format);
}
