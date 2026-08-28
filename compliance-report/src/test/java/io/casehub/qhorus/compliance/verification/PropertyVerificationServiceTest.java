package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.compliance.model.PropertyVerificationReport;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


class PropertyVerificationServiceTest {

    private PropertyVerificationService service;

    @BeforeEach
    void setUp() {
        service = new PropertyVerificationService();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aggregatesPropertyResults() {
        var passing = stubProperty("LIVENESS", true, 0);
        var failing = stubProperty("SAFETY", false, 0);

        Instance<VerificationProperty>        props = mock(Instance.class);
        Instance.Handle<VerificationProperty> h1    = mock(Instance.Handle.class);
        Instance.Handle<VerificationProperty> h2    = mock(Instance.Handle.class);
        when(h1.get()).thenReturn(passing);
        when(h1.getBean()).thenReturn(mock(jakarta.enterprise.inject.spi.Bean.class));
        when(h2.get()).thenReturn(failing);
        when(h2.getBean()).thenReturn(mock(jakarta.enterprise.inject.spi.Bean.class));
        doReturn(List.of(h1, h2)).when(props).handles();
        service.properties = props;

        Instant now = Instant.now();
        PropertyVerificationReport report =
                service.verify("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(report.totalProperties()).isEqualTo(2);
        assertThat(report.passed()).isEqualTo(1);
        assertThat(report.violated()).isEqualTo(1);
        assertThat(report.schemaVersion()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyPropertiesReturnsCleanReport() {
        Instance<VerificationProperty> props = mock(Instance.class);
        doReturn(List.of()).when(props).handles();
        service.properties = props;

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
