package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.judgment.JudgmentEventKinds;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
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

class JudgmentAttributionReportServiceTest {

    private JudgmentAttributionReportService service;
    private MessageLedgerEntryRepository mockRepo;
    private CausalGraphService mockCausalGraph;
    private TrustGateService mockTrustGate;
    private LedgerEntryRepository mockLedgerRepo;

    @BeforeEach
    void setUp() {
        service = new JudgmentAttributionReportService();
        mockRepo = mock(MessageLedgerEntryRepository.class);
        mockCausalGraph = mock(CausalGraphService.class);
        mockTrustGate = mock(TrustGateService.class);
        mockLedgerRepo = mock(LedgerEntryRepository.class);

        service.ledgerRepo = mockRepo;
        service.causalGraphService = mockCausalGraph;
        service.ledgerEntryRepository = mockLedgerRepo;
        service.trustGateServiceInstance = mockInstance(mockTrustGate);
        service.verificationServiceInstance = mockInstance(null);
    }

    @Test
    void generatesReportFromJudgmentEvents() {
        var judgmentId = UUID.randomUUID();
        var channelId = UUID.randomUUID();
        var correlationId = UUID.randomUUID().toString();
        var now = Instant.now();

        var yielded = buildEntry(JudgmentEventKinds.YIELDED, judgmentId, channelId,
                correlationId, now.minus(2, ChronoUnit.MINUTES), "agent-engine");
        var responded = buildEntry(JudgmentEventKinds.RESPONDED, judgmentId, channelId,
                correlationId, now.minus(1, ChronoUnit.MINUTES), "agent-reviewer");
        responded.evidenceQuality = 0.85;
        var verified = buildEntry(JudgmentEventKinds.VERIFIED, judgmentId, channelId,
                correlationId, now, "agent-engine");
        verified.verificationOutcome = "ACCEPTED";

        when(mockRepo.findJudgmentEvents(isNull(), eq(judgmentId), isNull(), isNull(), isNull()))
                .thenReturn(List.of(yielded, responded, verified));
        when(mockCausalGraph.buildGraph(eq(correlationId), anyInt(), isNull()))
                .thenReturn(new CausalGraph(correlationId, null, 1, List.of(channelId.toString()),
                        null, "FULFILLED", false, List.of(), List.of()));
        when(mockTrustGate.currentScore(anyString()))
                .thenReturn(OptionalDouble.of(0.9));
        when(mockLedgerRepo.findAttestationsByEntryId(any(), any()))
                .thenReturn(List.of());

        var report = service.generate(judgmentId, 200, null);

        assertThat(report.judgmentId()).isEqualTo(judgmentId.toString());
        assertThat(report.judgmentType()).isEqualTo("code_review");
        assertThat(report.verificationOutcome()).isEqualTo("ACCEPTED");
        assertThat(report.events()).hasSize(3);
        assertThat(report.events().get(0).eventKind()).isEqualTo(JudgmentEventKinds.YIELDED);
        assertThat(report.events().get(1).eventKind()).isEqualTo(JudgmentEventKinds.RESPONDED);
        assertThat(report.events().get(2).eventKind()).isEqualTo(JudgmentEventKinds.VERIFIED);
        assertThat(report.events().get(1).evidenceQuality()).isEqualTo(0.85);
        assertThat(report.totalDurationMs()).isGreaterThan(0);
        assertThat(report.channelCount()).isEqualTo(1);
        assertThat(report.schemaVersion()).isEqualTo(1);
    }

    @Test
    void returnsEmptyReportWhenNoEventsFound() {
        when(mockRepo.findJudgmentEvents(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        var report = service.generate(UUID.randomUUID(), 200, null);

        assertThat(report.events()).isEmpty();
        assertThat(report.causalNodes()).isEmpty();
        assertThat(report.causalEdges()).isEmpty();
        assertThat(report.channelCount()).isEqualTo(0);
    }

    @Test
    void extractsVerificationOutcomeFromVerifiedEvent() {
        var judgmentId = UUID.randomUUID();
        var channelId = UUID.randomUUID();

        var yielded = buildEntry(JudgmentEventKinds.YIELDED, judgmentId, channelId,
                null, Instant.now(), "engine");
        var verified = buildEntry(JudgmentEventKinds.VERIFIED, judgmentId, channelId,
                null, Instant.now(), "engine");
        verified.verificationOutcome = "REJECTED";

        when(mockRepo.findJudgmentEvents(any(), eq(judgmentId), any(), any(), any()))
                .thenReturn(List.of(yielded, verified));
        when(mockTrustGate.currentScore(anyString()))
                .thenReturn(OptionalDouble.empty());

        var report = service.generate(judgmentId, 200, null);

        assertThat(report.verificationOutcome()).isEqualTo("REJECTED");
    }

    @Test
    void multiChannelJudgmentReportsAllChannels() {
        var judgmentId = UUID.randomUUID();
        var ch1 = UUID.randomUUID();
        var ch2 = UUID.randomUUID();

        var yielded = buildEntry(JudgmentEventKinds.YIELDED, judgmentId, ch1,
                null, Instant.now(), "engine");
        var escalated = buildEntry(JudgmentEventKinds.ESCALATED, judgmentId, ch2,
                null, Instant.now(), "engine");

        when(mockRepo.findJudgmentEvents(any(), eq(judgmentId), any(), any(), any()))
                .thenReturn(List.of(yielded, escalated));
        when(mockTrustGate.currentScore(anyString()))
                .thenReturn(OptionalDouble.empty());

        var report = service.generate(judgmentId, 200, null);

        assertThat(report.channelCount()).isEqualTo(2);
        assertThat(report.channels()).containsExactlyInAnyOrder(ch1.toString(), ch2.toString());
    }

    private MessageLedgerEntry buildEntry(String toolName, UUID judgmentId, UUID channelId,
            String correlationId, Instant occurredAt, String actorId) {
        var e = new MessageLedgerEntry();
        e.subjectId = channelId;
        e.channelId = channelId;
        e.messageType = "EVENT";
        e.toolName = toolName;
        e.judgmentId = judgmentId;
        e.judgmentType = "code_review";
        e.correlationId = correlationId;
        e.occurredAt = occurredAt;
        e.actorId = actorId;
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
