package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.compliance.model.TrustHistoryReport;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustHistoryReportServiceTest {

    static final String TENANCY = "test-tenant";
    static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    static final Instant TO = Instant.parse("2026-08-31T23:59:59Z");

    @Mock TrustGateService trustGateService;
    @Mock Instance<TrustGateService> trustGateServiceInstance;

    TrustHistoryReportService service;

    @BeforeEach
    void setUp() {
        service = new TrustHistoryReportService();
        service.trustGateServiceInstance = trustGateServiceInstance;
    }

    @Test
    void generate_returnsCurrentScoreOnly_whenNoSnapshotTable() {
        when(trustGateServiceInstance.isResolvable()).thenReturn(true);
        when(trustGateServiceInstance.get()).thenReturn(trustGateService);
        when(trustGateService.currentScore("agent-1")).thenReturn(OptionalDouble.of(0.85));

        TrustHistoryReport report = service.generate("agent-1", FROM, TO, TENANCY);

        assertThat(report.actors()).hasSize(1);
        var actor = report.actors().getFirst();
        assertThat(actor.actorId()).isEqualTo("agent-1");
        assertThat(actor.currentScore()).isEqualTo(0.85);
        assertThat(actor.trajectory()).isEmpty();
        assertThat(actor.attestations()).isEmpty();
        assertThat(report.schemaVersion()).isEqualTo(1);
    }

    @Test
    void generate_unknownActor_returnsNullScore() {
        when(trustGateServiceInstance.isResolvable()).thenReturn(true);
        when(trustGateServiceInstance.get()).thenReturn(trustGateService);
        when(trustGateService.currentScore("unknown-agent")).thenReturn(OptionalDouble.empty());

        TrustHistoryReport report = service.generate("unknown-agent", FROM, TO, TENANCY);

        assertThat(report.actors()).hasSize(1);
        var actor = report.actors().getFirst();
        assertThat(actor.currentScore()).isNull();
        assertThat(actor.trajectory()).isEmpty();
    }

    @Test
    void generate_trustServiceUnavailable_returnsNullScore() {
        when(trustGateServiceInstance.isResolvable()).thenReturn(false);

        TrustHistoryReport report = service.generate("agent-1", FROM, TO, TENANCY);

        assertThat(report.actors()).hasSize(1);
        assertThat(report.actors().getFirst().currentScore()).isNull();
    }
}
