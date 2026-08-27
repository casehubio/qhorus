package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
import io.casehub.qhorus.api.spi.compliance.CompliancePostureProvider;
import io.casehub.qhorus.api.spi.compliance.PostureEntry;
import io.casehub.qhorus.api.spi.compliance.PostureStatus;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObligationReportServiceTest {

    static final String TENANCY = "test-tenant";
    static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    static final Instant TO = Instant.parse("2026-08-31T23:59:59Z");

    final UUID channelId = UUID.randomUUID();

    @Mock MessageLedgerEntryRepository ledgerRepo;
    @Mock CommitmentReader commitmentReader;
    @Mock ChannelStore channelStore;
    @Mock TrustGateService trustGateService;
    @Mock Instance<TrustGateService> trustGateServiceInstance;
    @Mock CompliancePostureProvider postureProvider;
    @Mock LedgerVerificationService verificationService;
    @Mock Instance<LedgerVerificationService> verificationServiceInstance;

    ObligationReportService service;

    @BeforeEach
    void setUp() {
        service = new ObligationReportService();
        service.ledgerRepo = ledgerRepo;
        service.commitmentReader = commitmentReader;
        service.channelStore = channelStore;
        service.trustGateServiceInstance = trustGateServiceInstance;
        service.postureProvider = postureProvider;
        service.verificationServiceInstance = verificationServiceInstance;
    }

    @Test
    void generate_perChannel_aggregatesOutcomeCounts() {
        Channel ch = channel(channelId, "test-channel");
        when(channelStore.find(channelId)).thenReturn(Optional.of(ch));

        when(ledgerRepo.countByOutcome(channelId, FROM, TO, TENANCY))
                .thenReturn(Map.of("COMMAND", 10L, "DONE", 7L, "FAILURE", 1L, "DECLINE", 1L, "HANDOFF", 1L));
        when(commitmentReader.findOpenByChannelId(channelId)).thenReturn(List.of());
        when(ledgerRepo.findStalledCommands(channelId, FROM, TENANCY)).thenReturn(List.of());
        when(commitmentReader.findByChannel(channelId)).thenReturn(List.of());
        when(postureProvider.getPosture(TENANCY, FROM, TO)).thenReturn(CompliancePosture.EMPTY);
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ObligationReport report = service.generate(channelId, FROM, TO, null, TENANCY);

        assertThat(report.channels()).hasSize(1);
        var summary = report.channels().getFirst();
        assertThat(summary.total()).isEqualTo(10);
        assertThat(summary.fulfilled()).isEqualTo(7);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.declined()).isEqualTo(1);
        assertThat(summary.delegated()).isEqualTo(1);
        assertThat(summary.fulfillmentRate()).isEqualTo(0.7);

        assertThat(report.totalCommands()).isEqualTo(10);
        assertThat(report.fulfilled()).isEqualTo(7);
        assertThat(report.overallFulfillmentRate()).isEqualTo(0.7);
    }

    @Test
    void generate_perAgent_aggregatesFromCommitments() {
        Channel ch = channel(channelId, "test-channel");
        when(channelStore.find(channelId)).thenReturn(Optional.of(ch));

        when(ledgerRepo.countByOutcome(channelId, FROM, TO, TENANCY)).thenReturn(Map.of());
        when(commitmentReader.findOpenByChannelId(channelId)).thenReturn(List.of());
        when(ledgerRepo.findStalledCommands(channelId, FROM, TENANCY)).thenReturn(List.of());

        Commitment fulfilled = commitment("agent-1", CommitmentState.FULFILLED, "2026-08-10T10:00:00Z");
        Commitment failed = commitment("agent-1", CommitmentState.FAILED, "2026-08-15T10:00:00Z");
        Commitment open = commitment("agent-1", CommitmentState.OPEN, "2026-08-20T10:00:00Z");
        when(commitmentReader.findByChannel(channelId)).thenReturn(List.of(fulfilled, failed, open));

        when(trustGateServiceInstance.isResolvable()).thenReturn(true);
        when(trustGateServiceInstance.get()).thenReturn(trustGateService);
        when(trustGateService.currentScore("agent-1")).thenReturn(OptionalDouble.of(0.8));
        when(postureProvider.getPosture(TENANCY, FROM, TO)).thenReturn(CompliancePosture.EMPTY);
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ObligationReport report = service.generate(channelId, FROM, TO, null, TENANCY);

        assertThat(report.agents()).hasSize(1);
        var agent = report.agents().getFirst();
        assertThat(agent.actorId()).isEqualTo("agent-1");
        assertThat(agent.total()).isEqualTo(3);
        assertThat(agent.fulfilled()).isEqualTo(1);
        assertThat(agent.failed()).isEqualTo(1);
        assertThat(agent.stillOpen()).isEqualTo(1);
        assertThat(agent.currentTrustScore()).isEqualTo(0.8);
    }

    @Test
    void generate_stillOpen_notBoundedToTimeWindow() {
        Channel ch = channel(channelId, "test-channel");
        when(channelStore.find(channelId)).thenReturn(Optional.of(ch));

        when(ledgerRepo.countByOutcome(channelId, FROM, TO, TENANCY)).thenReturn(Map.of());
        when(ledgerRepo.findStalledCommands(channelId, FROM, TENANCY)).thenReturn(List.of());

        Commitment beforeWindow = commitment("agent-1", CommitmentState.OPEN, "2026-07-01T10:00:00Z");
        Commitment inWindow = commitment("agent-1", CommitmentState.OPEN, "2026-08-15T10:00:00Z");
        when(commitmentReader.findOpenByChannelId(channelId)).thenReturn(List.of(beforeWindow, inWindow));
        when(commitmentReader.findByChannel(channelId)).thenReturn(List.of(beforeWindow, inWindow));

        when(trustGateServiceInstance.isResolvable()).thenReturn(false);
        when(postureProvider.getPosture(TENANCY, FROM, TO)).thenReturn(CompliancePosture.EMPTY);
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ObligationReport report = service.generate(channelId, FROM, TO, null, TENANCY);

        assertThat(report.channels().getFirst().stillOpen()).isEqualTo(2);
        assertThat(report.agents().getFirst().stillOpen()).isEqualTo(2);
    }

    @Test
    void generate_stalled_usesFromAsStalenessThreshold() {
        Channel ch = channel(channelId, "test-channel");
        when(channelStore.find(channelId)).thenReturn(Optional.of(ch));

        when(ledgerRepo.countByOutcome(channelId, FROM, TO, TENANCY)).thenReturn(Map.of());
        when(commitmentReader.findOpenByChannelId(channelId)).thenReturn(List.of());

        Commitment oldOpen = commitment("agent-1", CommitmentState.OPEN, "2026-07-15T10:00:00Z");
        Commitment recentOpen = commitment("agent-1", CommitmentState.OPEN, "2026-08-20T10:00:00Z");
        when(commitmentReader.findByChannel(channelId)).thenReturn(List.of(oldOpen, recentOpen));
        when(ledgerRepo.findStalledCommands(channelId, FROM, TENANCY)).thenReturn(List.of());

        when(trustGateServiceInstance.isResolvable()).thenReturn(false);
        when(postureProvider.getPosture(TENANCY, FROM, TO)).thenReturn(CompliancePosture.EMPTY);
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ObligationReport report = service.generate(channelId, FROM, TO, null, TENANCY);

        var agent = report.agents().getFirst();
        assertThat(agent.stalled()).isEqualTo(1);
    }

    @Test
    void generate_includesPosture_whenProviderReturnsData() {
        Channel ch = channel(channelId, "test-channel");
        when(channelStore.find(channelId)).thenReturn(Optional.of(ch));

        when(ledgerRepo.countByOutcome(channelId, FROM, TO, TENANCY)).thenReturn(Map.of());
        when(commitmentReader.findOpenByChannelId(channelId)).thenReturn(List.of());
        when(ledgerRepo.findStalledCommands(channelId, FROM, TENANCY)).thenReturn(List.of());
        when(commitmentReader.findByChannel(channelId)).thenReturn(List.of());
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        var entry = new PostureEntry("data-governance", PostureStatus.COMPLIANT, "OK", "evidence", Instant.now());
        when(postureProvider.getPosture(TENANCY, FROM, TO))
                .thenReturn(new CompliancePosture(List.of(entry)));

        ObligationReport report = service.generate(channelId, FROM, TO, null, TENANCY);

        assertThat(report.posture().entries()).hasSize(1);
        assertThat(report.posture().entries().getFirst().status()).isEqualTo(PostureStatus.COMPLIANT);
    }

    @Test
    void generate_posture_emptyWhenNoOpProvider() {
        Channel ch = channel(channelId, "test-channel");
        when(channelStore.find(channelId)).thenReturn(Optional.of(ch));

        when(ledgerRepo.countByOutcome(channelId, FROM, TO, TENANCY)).thenReturn(Map.of());
        when(commitmentReader.findOpenByChannelId(channelId)).thenReturn(List.of());
        when(ledgerRepo.findStalledCommands(channelId, FROM, TENANCY)).thenReturn(List.of());
        when(commitmentReader.findByChannel(channelId)).thenReturn(List.of());
        when(verificationServiceInstance.isResolvable()).thenReturn(false);
        when(postureProvider.getPosture(TENANCY, FROM, TO)).thenReturn(CompliancePosture.EMPTY);

        ObligationReport report = service.generate(channelId, FROM, TO, null, TENANCY);

        assertThat(report.posture()).isEqualTo(CompliancePosture.EMPTY);
    }

    @Test
    void generate_crossChannel_aggregatesAllChannels() {
        UUID ch1Id = UUID.randomUUID();
        UUID ch2Id = UUID.randomUUID();
        Channel ch1 = channel(ch1Id, "channel-1");
        Channel ch2 = channel(ch2Id, "channel-2");
        when(channelStore.scan(any())).thenReturn(List.of(ch1, ch2));

        when(ledgerRepo.countByOutcome(ch1Id, FROM, TO, TENANCY))
                .thenReturn(Map.of("COMMAND", 5L, "DONE", 3L));
        when(ledgerRepo.countByOutcome(ch2Id, FROM, TO, TENANCY))
                .thenReturn(Map.of("COMMAND", 5L, "DONE", 4L));
        when(commitmentReader.findOpenByChannelId(any())).thenReturn(List.of());
        when(ledgerRepo.findStalledCommands(any(), eq(FROM), eq(TENANCY))).thenReturn(List.of());
        when(commitmentReader.findByChannel(any())).thenReturn(List.of());
        when(postureProvider.getPosture(TENANCY, FROM, TO)).thenReturn(CompliancePosture.EMPTY);
        when(verificationServiceInstance.isResolvable()).thenReturn(false);

        ObligationReport report = service.generate(null, FROM, TO, null, TENANCY);

        assertThat(report.channels()).hasSize(2);
        assertThat(report.totalCommands()).isEqualTo(10);
        assertThat(report.fulfilled()).isEqualTo(7);
    }

    private Channel channel(UUID id, String name) {
        return new Channel(id, name, null, ChannelSemantic.APPEND,
                List.of(), List.of(), List.of(), null, null,
                null, null, false, false, null,
                List.of(), List.of(), List.of(), null, null,
                List.of(), null, TENANCY, Instant.now(), null);
    }

    private Commitment commitment(String obligor, CommitmentState state, String createdAt) {
        return Commitment.builder()
                .id(UUID.randomUUID())
                .channelId(channelId)
                .messageType(MessageType.COMMAND)
                .obligor(obligor)
                .state(state)
                .tenancyId(TENANCY)
                .createdAt(Instant.parse(createdAt))
                .build();
    }
}
