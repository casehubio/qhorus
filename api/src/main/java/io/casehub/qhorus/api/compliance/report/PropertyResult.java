package io.casehub.qhorus.api.compliance.report;



import java.util.List;

public record PropertyResult(
        String propertyName,
        String ctlFormula,
        boolean passed,
        int violationCount,
        int remediationsApplied,
        List<PropertyViolation> violations
) {}
