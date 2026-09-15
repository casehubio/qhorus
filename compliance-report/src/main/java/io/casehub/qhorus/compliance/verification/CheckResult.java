package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.api.compliance.report.PropertyViolation;

import java.util.List;

public record CheckResult(List<PropertyViolation> violations, int remediationsAvailable) {
    public boolean passed() {
        return violations.isEmpty();
    }
}
