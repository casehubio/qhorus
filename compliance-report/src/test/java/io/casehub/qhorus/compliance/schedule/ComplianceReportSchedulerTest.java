package io.casehub.qhorus.compliance.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecord;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceReportSchedulerTest {

    @Mock ComplianceReportScheduleStore scheduleStore;
    @Mock ComplianceReportStorageService storageService;
    @Mock ObligationReportService obligationService;
    @Mock ViolationReportService violationService;
    @Mock Event<ComplianceReportGeneratedEvent> generatedEvent;

    ComplianceReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ComplianceReportScheduler();
        scheduler.scheduleStore = scheduleStore;
        scheduler.storageService = storageService;
        scheduler.obligationService = obligationService;
        scheduler.violationService = violationService;
        scheduler.generatedEvent = generatedEvent;
        scheduler.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        scheduler.retentionDays = java.util.Optional.empty();
    }

    @Test
    void sweep_generatesDueReport() {
        ComplianceReportSchedule schedule = obligationSchedule();
        schedule.lastRunAt = Instant.now().minusSeconds(7200);
        when(scheduleStore.findEnabled()).thenReturn(List.of(schedule));

        when(obligationService.generate(any(), any(), any(), any(), any())).thenReturn(emptyObligationReport());
        ComplianceReportRecord record = new ComplianceReportRecord();
        record.id = UUID.randomUUID();
        record.artefactId = UUID.randomUUID();
        when(storageService.store(any(), any(), any(), any(), any())).thenReturn(record);

        scheduler.sweep();

        verify(storageService).store(eq(ReportType.OBLIGATION), any(), eq(ReportFormat.JSON), eq(schedule.id), eq(schedule.tenancyId));
        verify(scheduleStore).updateLastRunAt(eq(schedule.id), any());
    }

    @Test
    void sweep_skipsNotDueReport() {
        ComplianceReportSchedule schedule = obligationSchedule();
        schedule.lastRunAt = Instant.now().minusSeconds(1800);
        when(scheduleStore.findEnabled()).thenReturn(List.of(schedule));

        scheduler.sweep();

        verify(storageService, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    void sweep_updatesLastRunAt() {
        ComplianceReportSchedule schedule = obligationSchedule();
        schedule.lastRunAt = Instant.now().minusSeconds(7200);
        when(scheduleStore.findEnabled()).thenReturn(List.of(schedule));

        when(obligationService.generate(any(), any(), any(), any(), any())).thenReturn(emptyObligationReport());
        ComplianceReportRecord record = new ComplianceReportRecord();
        record.id = UUID.randomUUID();
        record.artefactId = UUID.randomUUID();
        when(storageService.store(any(), any(), any(), any(), any())).thenReturn(record);

        scheduler.sweep();

        verify(scheduleStore).updateLastRunAt(eq(schedule.id), any(Instant.class));
    }

    @Test
    void sweep_errorIsolation_continuesToNextSchedule() {
        ComplianceReportSchedule s1 = obligationSchedule();
        s1.lastRunAt = Instant.now().minusSeconds(7200);
        ComplianceReportSchedule s2 = obligationSchedule();
        s2.id = UUID.randomUUID();
        s2.tenancyId = "tenant-2";
        s2.lastRunAt = Instant.now().minusSeconds(7200);

        when(scheduleStore.findEnabled()).thenReturn(List.of(s1, s2));

        when(obligationService.generate(any(), any(), any(), any(), eq("test-tenant")))
                .thenThrow(new RuntimeException("channel deleted"));
        when(obligationService.generate(any(), any(), any(), any(), eq("tenant-2")))
                .thenReturn(emptyObligationReport());

        ComplianceReportRecord record = new ComplianceReportRecord();
        record.id = UUID.randomUUID();
        record.artefactId = UUID.randomUUID();
        when(storageService.store(any(), any(), any(), any(), any())).thenReturn(record);

        scheduler.sweep();

        verify(storageService, times(1)).store(eq(ReportType.OBLIGATION), any(), any(), eq(s2.id), eq("tenant-2"));
    }

    @Test
    void sweep_nullLastRunAt_treatsAsEpoch() {
        ComplianceReportSchedule schedule = obligationSchedule();
        schedule.lastRunAt = null;
        when(scheduleStore.findEnabled()).thenReturn(List.of(schedule));

        when(obligationService.generate(any(), any(), any(), any(), any())).thenReturn(emptyObligationReport());
        ComplianceReportRecord record = new ComplianceReportRecord();
        record.id = UUID.randomUUID();
        record.artefactId = UUID.randomUUID();
        when(storageService.store(any(), any(), any(), any(), any())).thenReturn(record);

        scheduler.sweep();

        verify(storageService).store(any(), any(), any(), any(), any());
    }

    @Test
    void purgeExpired_deletesOldReports() {
        scheduler.retentionDays = java.util.Optional.of(90);

        ComplianceReportRecord old = new ComplianceReportRecord();
        old.id          = UUID.randomUUID();
        old.reportType  = ReportType.OBLIGATION;
        old.tenancyId   = "test-tenant";
        old.generatedAt = Instant.now().minus(java.time.Duration.ofDays(100));

        when(storageService.findOlderThan(any(Instant.class))).thenReturn(java.util.List.of(old));

        scheduler.purgeExpired();

        verify(storageService).delete(old.id);
    }

    @Test
    void purgeExpired_skipsWhenRetentionNotConfigured() {
        scheduler.retentionDays = java.util.Optional.empty();

        scheduler.purgeExpired();

        verify(storageService, never()).findOlderThan(any());
        verify(storageService, never()).delete(any());
    }

    @Test
    void purgeExpired_errorIsolation_continuesToNextReport() {
        scheduler.retentionDays = java.util.Optional.of(90);

        ComplianceReportRecord r1 = new ComplianceReportRecord();
        r1.id          = UUID.randomUUID();
        r1.reportType  = ReportType.OBLIGATION;
        r1.tenancyId   = "t1";
        r1.generatedAt = Instant.now().minus(java.time.Duration.ofDays(100));

        ComplianceReportRecord r2 = new ComplianceReportRecord();
        r2.id          = UUID.randomUUID();
        r2.reportType  = ReportType.VIOLATION;
        r2.tenancyId   = "t2";
        r2.generatedAt = Instant.now().minus(java.time.Duration.ofDays(200));

        when(storageService.findOlderThan(any(Instant.class))).thenReturn(java.util.List.of(r1, r2));
        org.mockito.Mockito.doThrow(new RuntimeException("artefact locked")).when(storageService).delete(r1.id);

        scheduler.purgeExpired();

        verify(storageService).delete(r1.id);
        verify(storageService).delete(r2.id);
    }


    private ComplianceReportSchedule obligationSchedule() {
        ComplianceReportSchedule s = new ComplianceReportSchedule();
        s.id = UUID.randomUUID();
        s.reportType = ReportType.OBLIGATION;
        s.format = ReportFormat.JSON;
        s.tenancyId = "test-tenant";
        s.enabled = true;
        s.scheduleJson = "{\"type\":\"interval\",\"period\":\"PT1H\"}";
        return s;
    }

    private ObligationReport emptyObligationReport() {
        return new ObligationReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, 0.0,
                CompliancePosture.EMPTY, null, Instant.now(), 1);
    }
}
