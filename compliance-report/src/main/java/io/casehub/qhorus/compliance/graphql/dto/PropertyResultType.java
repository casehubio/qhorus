package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.PropertyResult;

import java.util.List;

public record PropertyResultType(
        String propertyName,
        String ctlFormula,
        boolean passed,
        int violationCount,
        int remediationsApplied,
        List<PropertyViolationType> violations
) {
    public static PropertyResultType from(PropertyResult r) {
        return new PropertyResultType(
                r.propertyName(), r.ctlFormula(), r.passed(),
                r.violationCount(), r.remediationsApplied(),
                r.violations().stream().map(PropertyViolationType::from).toList());
    }
}
