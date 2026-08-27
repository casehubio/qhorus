package io.casehub.qhorus.compliance.graphql;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceReportScheduleType;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceScheduleInput;
import io.casehub.qhorus.compliance.graphql.dto.ComplianceScheduleUpdateInput;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.schedule.ComplianceReportSchedule;
import io.casehub.qhorus.compliance.schedule.ComplianceReportScheduleStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceMutationResolverTest {

    static final String TENANCY = "test-tenant";

    @Mock ComplianceReportScheduleStore scheduleStore;
    @Mock ComplianceReportStorageService storageService;
    @Mock CurrentPrincipal currentPrincipal;

    ComplianceMutationResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ComplianceMutationResolver();
        resolver.scheduleStore = scheduleStore;
        resolver.storageService = storageService;
        resolver.currentPrincipal = currentPrincipal;

    }

    @Test
    void createComplianceSchedule_persistsAndReturnsType() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        ComplianceScheduleInput input = new ComplianceScheduleInput(
                ReportType.OBLIGATION, null, "{\"type\":\"interval\",\"period\":\"PT24H\"}", ReportFormat.JSON);

        ComplianceReportSchedule saved = scheduleEntity();
        when(scheduleStore.save(any())).thenReturn(saved);

        ComplianceReportScheduleType result = resolver.createComplianceSchedule(input);

        assertThat(result.reportType()).isEqualTo(ReportType.OBLIGATION);
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.enabled()).isTrue();

        ArgumentCaptor<ComplianceReportSchedule> captor = ArgumentCaptor.forClass(ComplianceReportSchedule.class);
        verify(scheduleStore).save(captor.capture());
        assertThat(captor.getValue().tenancyId).isEqualTo(TENANCY);
        assertThat(captor.getValue().enabled).isTrue();
    }

    @Test
    void updateComplianceSchedule_togglesEnabled() {
        ComplianceReportSchedule existing = scheduleEntity();
        existing.enabled = true;
        when(scheduleStore.findById(existing.id)).thenReturn(Optional.of(existing));
        when(scheduleStore.save(any())).thenReturn(existing);

        ComplianceScheduleUpdateInput input = new ComplianceScheduleUpdateInput(existing.id, false, null);
        resolver.updateComplianceSchedule(input);

        assertThat(existing.enabled).isFalse();
        verify(scheduleStore).save(existing);
    }

    @Test
    void updateComplianceSchedule_updatesScheduleJson() {
        ComplianceReportSchedule existing = scheduleEntity();
        when(scheduleStore.findById(existing.id)).thenReturn(Optional.of(existing));
        when(scheduleStore.save(any())).thenReturn(existing);

        String newJson = "{\"type\":\"interval\",\"period\":\"PT48H\"}";
        ComplianceScheduleUpdateInput input = new ComplianceScheduleUpdateInput(existing.id, null, newJson);
        resolver.updateComplianceSchedule(input);

        assertThat(existing.scheduleJson).isEqualTo(newJson);
    }

    @Test
    void deleteComplianceSchedule_callsStore() {
        UUID id = UUID.randomUUID();
        boolean result = resolver.deleteComplianceSchedule(id);

        assertThat(result).isTrue();
        verify(scheduleStore).delete(id);
    }

    @Test
    void deleteComplianceReport_callsStorageService() {
        UUID id = UUID.randomUUID();
        boolean result = resolver.deleteComplianceReport(id);

        assertThat(result).isTrue();
        verify(storageService).delete(id);
    }

    private ComplianceReportSchedule scheduleEntity() {
        ComplianceReportSchedule s = new ComplianceReportSchedule();
        s.id = UUID.randomUUID();
        s.reportType = ReportType.OBLIGATION;
        s.scheduleJson = "{\"type\":\"interval\",\"period\":\"PT24H\"}";
        s.format = ReportFormat.JSON;
        s.tenancyId = TENANCY;
        s.enabled = true;
        s.createdAt = Instant.now();
        return s;
    }
}
