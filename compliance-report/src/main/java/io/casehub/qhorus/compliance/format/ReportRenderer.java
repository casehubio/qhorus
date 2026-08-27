package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.ReportFormat;

public interface ReportRenderer {
    String contentType();
    byte[] render(Object report);
    boolean supports(ReportFormat format);
}
