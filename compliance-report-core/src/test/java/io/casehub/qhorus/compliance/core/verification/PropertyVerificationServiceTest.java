package io.casehub.qhorus.compliance.core.verification;

import io.casehub.qhorus.api.compliance.report.PropertyVerificationReport;
import io.casehub.qhorus.api.compliance.report.PropertyViolation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyVerificationServiceTest {

    @Test
    void aggregatesPropertyResults() {
        var passing = stubProperty("LIVENESS", true, 0);
        var failing = stubProperty("SAFETY", false, 0);

        var service = new PropertyVerificationService(List.of(passing, failing));

        Instant now = Instant.now();
        PropertyVerificationReport report =
                service.verify("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(report.totalProperties()).isEqualTo(2);
        assertThat(report.passed()).isEqualTo(1);
        assertThat(report.violated()).isEqualTo(1);
        assertThat(report.schemaVersion()).isEqualTo(1);
    }

    @Test
    void emptyPropertiesReturnsCleanReport() {
        var service = new PropertyVerificationService(List.of());

        Instant now = Instant.now();
        PropertyVerificationReport report =
                service.verify("default", now.minus(1, ChronoUnit.DAYS), now);

        assertThat(report.totalProperties()).isZero();
        assertThat(report.passed()).isZero();
        assertThat(report.violated()).isZero();
    }

    private VerificationProperty stubProperty(String name, boolean passes, int remediations) {
        return new VerificationProperty() {
            @Override public String name() { return name; }
            @Override public String ctlFormula() { return "AG(...)"; }
            @Override public String description() { return name + " property"; }
            @Override public CheckResult check(String tenancyId, Instant from, Instant to) {
                if (passes) return new CheckResult(List.of(), 0);
                return new CheckResult(
                        List.of(new PropertyViolation(name, "violation", "ev", from, "HIGH")),
                        remediations);
            }
        };
    }
}
