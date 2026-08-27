package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.judgment.JudgmentEventKinds;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JudgmentFulfillmentReportServiceTest {

    private JudgmentFulfillmentReportService service;
    private MessageLedgerEntryRepository mockRepo;
    private TrustGateService mockTrustGate;

    @BeforeEach
    void setUp() {
        service = new JudgmentFulfillmentReportService();
        mockRepo = mock(MessageLedgerEntryRepository.class);
        mockTrustGate = mock(TrustGateService.class);

        service.ledgerRepo = mockRepo;
        service.trustGateServiceInstance = mockInstance(mockTrustGate);
        service.verificationServiceInstance = mockInstance(null);
    }

    @Test
    void aggregatesOutcomesByType() {
        var now = Instant.now();
        var from = now.minus(1, ChronoUnit.DAYS);

        when(mockRepo.countJudgmentOutcomes(eq(from), eq(now), isNull()))
                .thenReturn(List.of(
                        new Object[]{"code_review", JudgmentEventKinds.VERIFIED, "ACCEPTED", 3L},
                        new Object[]{"code_review", JudgmentEventKinds.VERIFIED, "REJECTED", 1L},
                        new Object[]{"quality_check", JudgmentEventKinds.VERIFIED, "ACCEPTED", 2L},
                        new Object[]{"code_review", JudgmentEventKinds.ESCALATED, null, 1L}
                ));
        when(mockRepo.findPendingJudgments(isNull())).thenReturn(List.of());
        when(mockRepo.findJudgmentEvents(any(), any(), any(), any(), any())).thenReturn(List.of());

        var report = service.generate(from, now, null, null, null);

        assertThat(report.totalJudgments()).isEqualTo(7);
        assertThat(report.accepted()).isEqualTo(5);
        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.escalated()).isEqualTo(1);
        assertThat(report.byType()).hasSize(2);

        var codeReview = report.byType().stream()
                .filter(t -> "code_review".equals(t.judgmentType())).findFirst().orElseThrow();
        assertThat(codeReview.accepted()).isEqualTo(3);
        assertThat(codeReview.rejected()).isEqualTo(1);
        assertThat(codeReview.escalated()).isEqualTo(1);
        assertThat(codeReview.total()).isEqualTo(5);
    }

    @Test
    void includesUnboundedPending() {
        var now = Instant.now();
        var from = now.minus(1, ChronoUnit.DAYS);

        when(mockRepo.countJudgmentOutcomes(any(), any(), any())).thenReturn(List.of());

        var pendingEntry = buildEntry(JudgmentEventKinds.YIELDED, UUID.randomUUID(),
                "code_review", now.minus(5, ChronoUnit.DAYS));
        when(mockRepo.findPendingJudgments(isNull())).thenReturn(List.of(pendingEntry));
        when(mockRepo.findJudgmentEvents(any(), any(), any(), any(), any())).thenReturn(List.of());

        var report = service.generate(from, now, null, null, null);

        assertThat(report.pending()).isEqualTo(1);
        assertThat(report.byType()).hasSize(1);
        assertThat(report.byType().getFirst().pending()).isEqualTo(1);
    }

    @Test
    void computesEvidenceQualityAverage() {
        var now = Instant.now();
        var from = now.minus(1, ChronoUnit.DAYS);
        var judgmentId1 = UUID.randomUUID();
        var judgmentId2 = UUID.randomUUID();
        var channelId = UUID.randomUUID();

        when(mockRepo.countJudgmentOutcomes(any(), any(), any())).thenReturn(List.of());
        when(mockRepo.findPendingJudgments(any())).thenReturn(List.of());

        var yielded1 = buildEntry(JudgmentEventKinds.YIELDED, judgmentId1, "code_review",
                now.minus(2, ChronoUnit.HOURS));
        yielded1.channelId = channelId;
        var responded1 = buildEntry(JudgmentEventKinds.RESPONDED, judgmentId1, "code_review",
                now.minus(1, ChronoUnit.HOURS));
        responded1.evidenceQuality = 0.8;
        responded1.actorId = "reviewer-1";
        responded1.channelId = channelId;

        var yielded2 = buildEntry(JudgmentEventKinds.YIELDED, judgmentId2, "code_review",
                now.minus(90, ChronoUnit.MINUTES));
        yielded2.channelId = channelId;
        var responded2 = buildEntry(JudgmentEventKinds.RESPONDED, judgmentId2, "code_review",
                now.minus(30, ChronoUnit.MINUTES));
        responded2.evidenceQuality = 0.6;
        responded2.actorId = "reviewer-2";
        responded2.channelId = channelId;

        when(mockRepo.findJudgmentEvents(isNull(), isNull(), eq(from), eq(now), isNull()))
                .thenReturn(List.of(yielded1, responded1, yielded2, responded2));
        when(mockTrustGate.currentScore(anyString())).thenReturn(OptionalDouble.of(0.85));

        var report = service.generate(from, now, null, null, null);

        assertThat(report.averageEvidenceQuality()).isEqualTo(0.7);
        assertThat(report.byCaller()).hasSize(2);
    }

    @Test
    void emptyReportWhenNoData() {
        var now = Instant.now();
        var from = now.minus(1, ChronoUnit.DAYS);

        when(mockRepo.countJudgmentOutcomes(any(), any(), any())).thenReturn(List.of());
        when(mockRepo.findPendingJudgments(any())).thenReturn(List.of());
        when(mockRepo.findJudgmentEvents(any(), any(), any(), any(), any())).thenReturn(List.of());

        var report = service.generate(from, now, null, null, null);

        assertThat(report.totalJudgments()).isEqualTo(0);
        assertThat(report.byType()).isEmpty();
        assertThat(report.byCaller()).isEmpty();
        assertThat(report.schemaVersion()).isEqualTo(1);
    }

    private MessageLedgerEntry buildEntry(String toolName, UUID judgmentId,
            String judgmentType, Instant occurredAt) {
        var e = new MessageLedgerEntry();
        e.subjectId = UUID.randomUUID();
        e.channelId = UUID.randomUUID();
        e.messageType = "EVENT";
        e.toolName = toolName;
        e.judgmentId = judgmentId;
        e.judgmentType = judgmentType;
        e.occurredAt = occurredAt;
        e.actorId = "test-actor";
        e.sequenceNumber = 1;
        e.entryType = io.casehub.ledger.api.model.LedgerEntryType.EVENT;
        e.actorType = io.casehub.platform.api.identity.ActorType.AGENT;
        e.actorRole = "test-role";
        e.tenancyId = "DEFAULT";
        return e;
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> mockInstance(T value) {
        Instance<T> instance = mock(Instance.class);
        if (value != null) {
            when(instance.isResolvable()).thenReturn(true);
            when(instance.get()).thenReturn(value);
        } else {
            when(instance.isResolvable()).thenReturn(false);
        }
        return instance;
    }
}
