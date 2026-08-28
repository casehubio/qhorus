package io.casehub.qhorus.compliance.model;

import io.casehub.qhorus.compliance.verification.PropertyViolation;

import java.util.List;

public record PropertyResult(
        String propertyName,
        String ctlFormula,
        boolean passed,
        int violationCount,
        int remediationsApplied,
        List<PropertyViolation> violations
) {}
