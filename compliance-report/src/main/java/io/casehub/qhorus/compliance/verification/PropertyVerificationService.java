package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.compliance.model.PropertyResult;
import io.casehub.qhorus.compliance.model.PropertyVerificationReport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PropertyVerificationService {

    private static final Logger LOG = Logger.getLogger(PropertyVerificationService.class);
    private static final int SCHEMA_VERSION = 1;

    @Inject
    @Any
    Instance<VerificationProperty> properties;

    // Merkle root omitted — property verification spans multiple subjects; no single root is meaningful

    public PropertyVerificationReport verify(String tenancyId, Instant from, Instant to) {
        List<PropertyResult> results           = new ArrayList<>();
        int                  totalRemediations = 0;

        for (var handle : properties.handles()) {
            try {
                var         property    = handle.get();
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
                LOG.warnf(e, "Property check failed: %s", handle.getBean().getBeanClass().getSimpleName());
            } finally {
                handle.close();
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
