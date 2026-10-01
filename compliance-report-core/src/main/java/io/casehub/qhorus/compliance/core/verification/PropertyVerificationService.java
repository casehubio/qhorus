package io.casehub.qhorus.compliance.core.verification;

import io.casehub.qhorus.api.compliance.report.PropertyResult;
import io.casehub.qhorus.api.compliance.report.PropertyVerificationReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PropertyVerificationService {

    private static final Logger LOG = Logger.getLogger(PropertyVerificationService.class.getName());
    private static final int SCHEMA_VERSION = 1;

    private final List<VerificationProperty> properties;

    public PropertyVerificationService(List<VerificationProperty> properties) {
        this.properties = properties;
    }

    public PropertyVerificationReport verify(String tenancyId, Instant from, Instant to) {
        List<PropertyResult> results           = new ArrayList<>();
        int                  totalRemediations = 0;

        for (VerificationProperty property : properties) {
            try {
                CheckResult checkResult = property.check(tenancyId, from, to);

                int remediations = 0;
                if (property instanceof RemediatingProperty remediating
                    && checkResult.remediationsAvailable() > 0) {
                    remediations = remediating.remediate(tenancyId, from, to);
                    totalRemediations += remediations;
                }

                results.add(new PropertyResult(
                        property.name(),
                        property.ctlFormula(),
                        checkResult.passed(),
                        checkResult.violations().size(),
                        remediations,
                        checkResult.violations()));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Property check failed: " + property.getClass().getSimpleName(), e);
            }
        }

        int passed   = (int) results.stream().filter(PropertyResult::passed).count();
        int violated = results.size() - passed;

        return new PropertyVerificationReport(
                from, to, results,
                results.size(), passed, violated,
                totalRemediations,
                null,
                Instant.now(),
                SCHEMA_VERSION);
    }
}
