package io.casehub.qhorus.graphql.audit;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.qhorus.api.audit.AttestationSummary;
import io.casehub.qhorus.api.audit.CausalGraph;
import io.casehub.qhorus.api.audit.LedgerEntryView;
import io.casehub.qhorus.api.audit.PeerAttestor;
import io.casehub.qhorus.api.audit.ReviewerProvider;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.store.CausalGraphReader;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.LedgerReader;
import io.casehub.qhorus.api.store.MessageReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    private AuditService service;
    private LedgerReader ledgerReader;
    private CausalGraphReader causalGraphReader;
    private CommitmentReader commitmentReader;
    private MessageReader messageReader;
    private PeerAttestor peerAttestor;
    private ReviewerProvider reviewerProvider;
    private MessageDispatcher messageDispatcher;

    private static final UUID CHANNEL_ID = UUID.randomUUID();
    private static final String TENANCY_ID = "test-tenant";

    @BeforeEach
    void setUp() {
        ledgerReader = mock(LedgerReader.class);
        causalGraphReader = mock(CausalGraphReader.class);
        commitmentReader = mock(CommitmentReader.class);
        messageReader = mock(MessageReader.class);
        peerAttestor = mock(PeerAttestor.class);
        reviewerProvider = mock(ReviewerProvider.class);
        messageDispatcher = mock(MessageDispatcher.class);
        service = new AuditService(ledgerReader, causalGraphReader,
                commitmentReader, messageReader, peerAttestor,
                reviewerProvider, messageDispatcher);
    }

    @Test
    void ledgerEntriesReturnsFilteredEntries() {
        var entry = ledgerEntry("COMMAND");
        when(ledgerReader.listEntries(eq(CHANNEL_ID), any(), any(), any(),
                any(), any(), eq(false), eq(20), eq(TENANCY_ID)))
                .thenReturn(List.of(entry));

        var result = service.ledgerEntries(CHANNEL_ID, null, null, null,
                null, null, null, null, TENANCY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).messageType()).isEqualTo("COMMAND");
    }

    @Test
    void ledgerEntriesClampLimitTo100() {
        when(ledgerReader.listEntries(eq(CHANNEL_ID), any(), any(), any(),
                any(), any(), eq(false), eq(100), eq(TENANCY_ID)))
                .thenReturn(List.of());

        service.ledgerEntries(CHANNEL_ID, null, null, null,
                null, null, null, 500, TENANCY_ID);

        verify(ledgerReader).listEntries(eq(CHANNEL_ID), any(), any(), any(),
                any(), any(), eq(false), eq(100), eq(TENANCY_ID));
    }

    @Test
    void ledgerEntriesParsesTypeFilter() {
        when(ledgerReader.listEntries(eq(CHANNEL_ID), any(), any(), any(),
                any(), any(), eq(false), anyInt(), eq(TENANCY_ID)))
                .thenReturn(List.of());

        service.ledgerEntries(CHANNEL_ID, "COMMAND,DONE", null, null,
                null, null, null, null, TENANCY_ID);

        verify(ledgerReader).listEntries(eq(CHANNEL_ID),
                argThat(s -> s != null && s.contains("COMMAND") && s.contains("DONE")),
                any(), any(), any(), any(), eq(false), anyInt(), eq(TENANCY_ID));
    }

    @Test
    void ledgerEntriesInvalidSortThrows() {
        assertThatThrownBy(() -> service.ledgerEntries(CHANNEL_ID, null, null,
                null, null, null, "invalid", null, TENANCY_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void obligationChainComputesFromEntries() {
        var now = Instant.now();
        var cmd = ledgerEntryWith("COMMAND", "agent-1", "corr-1", now.minusSeconds(10));
        var done = ledgerEntryWith("DONE", "agent-2", "corr-1", now);
        when(ledgerReader.findAllByCorrelationId(CHANNEL_ID, "corr-1", TENANCY_ID))
                .thenReturn(List.of(cmd, done));
        when(commitmentReader.findByCorrelationId("corr-1")).thenReturn(Optional.empty());

        var result = service.obligationChain(CHANNEL_ID, "corr-1", TENANCY_ID);

        assertThat(result.correlationId()).isEqualTo("corr-1");
        assertThat(result.initiator()).isEqualTo("agent-1");
        assertThat(result.resolution()).isEqualTo("DONE");
        assertThat(result.elapsedSeconds()).isEqualTo(10L);
        assertThat(result.participants()).containsExactly("agent-1", "agent-2");
    }

    @Test
    void obligationChainReturnsEmptyForUnknown() {
        when(ledgerReader.findAllByCorrelationId(CHANNEL_ID, "unknown", TENANCY_ID))
                .thenReturn(List.of());

        var result = service.obligationChain(CHANNEL_ID, "unknown", TENANCY_ID);

        assertThat(result.correlationId()).isEqualTo("unknown");
        assertThat(result.initiator()).isNull();
        assertThat(result.participants()).isEmpty();
    }

    @Test
    void causalChainWithChannelReturnsChannelScoped() {
        var entry = ledgerEntry("COMMAND");
        when(ledgerReader.findAncestorChain(CHANNEL_ID, entry.entryId(), TENANCY_ID))
                .thenReturn(List.of(entry));

        var result = service.causalChain(CHANNEL_ID, entry.entryId(), TENANCY_ID);

        assertThat(result).hasSize(1);
        verify(ledgerReader).findAncestorChain(CHANNEL_ID, entry.entryId(), TENANCY_ID);
        verify(ledgerReader, never()).findAncestorChainCrossChannel(any(), any());
    }

    @Test
    void causalChainWithoutChannelReturnsCrossChannel() {
        var entryId = UUID.randomUUID();
        var entry = ledgerEntry("COMMAND");
        when(ledgerReader.findAncestorChainCrossChannel(entryId, TENANCY_ID))
                .thenReturn(List.of(entry));

        var result = service.causalChain(null, entryId, TENANCY_ID);

        assertThat(result).hasSize(1);
        verify(ledgerReader).findAncestorChainCrossChannel(entryId, TENANCY_ID);
    }

    @Test
    void causalGraphDelegates() {
        var graph = new CausalGraph("corr-1", null, 0, List.of(), null,
                "OPEN", false, List.of(), List.of());
        when(causalGraphReader.buildGraph("corr-1", 100, TENANCY_ID)).thenReturn(graph);

        var result = service.causalGraph("corr-1", null, TENANCY_ID);

        assertThat(result.correlationId()).isEqualTo("corr-1");
        verify(causalGraphReader).buildGraph("corr-1", 100, TENANCY_ID);
    }

    @Test
    void causalGraphClampsLimit() {
        var graph = new CausalGraph("corr-1", null, 0, List.of(), null,
                "OPEN", false, List.of(), List.of());
        when(causalGraphReader.buildGraph(eq("corr-1"), eq(500), eq(TENANCY_ID)))
                .thenReturn(graph);

        service.causalGraph("corr-1", 999, TENANCY_ID);

        verify(causalGraphReader).buildGraph("corr-1", 500, TENANCY_ID);
    }

    @Test
    void stalledObligationsComputesStalledFor() {
        var entry = ledgerEntryWith("COMMAND", "agent-1", "corr-1",
                Instant.now().minusSeconds(60));
        when(ledgerReader.findStalledCommands(eq(CHANNEL_ID), any(), eq(TENANCY_ID)))
                .thenReturn(List.of(entry));

        var result = service.stalledObligations(CHANNEL_ID, 30, TENANCY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stalledForSeconds()).isGreaterThanOrEqualTo(59);
    }

    @Test
    void obligationStatsComputesArithmetic() {
        when(ledgerReader.countByOutcome(CHANNEL_ID, TENANCY_ID))
                .thenReturn(Map.of("COMMAND", 10L, "DONE", 6L, "FAILURE", 1L,
                        "DECLINE", 1L, "HANDOFF", 1L));
        when(ledgerReader.findStalledCommands(eq(CHANNEL_ID), any(), eq(TENANCY_ID)))
                .thenReturn(List.of(ledgerEntry("COMMAND")));

        var result = service.obligationStats(CHANNEL_ID, TENANCY_ID);

        assertThat(result.totalCommands()).isEqualTo(10);
        assertThat(result.fulfilled()).isEqualTo(6);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.declined()).isEqualTo(1);
        assertThat(result.delegated()).isEqualTo(1);
        assertThat(result.stillOpen()).isEqualTo(1);
        assertThat(result.stalled()).isEqualTo(1);
        assertThat(result.fulfillmentRate()).isCloseTo(0.6,
                org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void telemetrySummaryAggregatesPerTool() {
        var e1 = eventEntry("tool-a", 100L, 50L);
        var e2 = eventEntry("tool-a", 200L, 80L);
        var e3 = eventEntry("tool-b", 300L, 100L);
        when(ledgerReader.findEventsSince(eq(CHANNEL_ID), any(), eq(TENANCY_ID)))
                .thenReturn(List.of(e1, e2, e3));

        var result = service.telemetrySummary(CHANNEL_ID, null, TENANCY_ID);

        assertThat(result.totalEvents()).isEqualTo(3);
        assertThat(result.byTool()).containsKey("tool-a");
        assertThat(result.byTool().get("tool-a").count()).isEqualTo(2);
        assertThat(result.byTool().get("tool-a").avgDurationMs()).isEqualTo(150L);
        assertThat(result.totalTokens()).isEqualTo(230L);
    }

    @Test
    void attestDelegatesToPeerAttestor() {
        var entryId = UUID.randomUUID();
        var summary = new AttestationSummary(UUID.randomUUID(), "ENDORSED",
                "reviewer-1", "peer", null, 0.4, Instant.now());
        when(peerAttestor.write(entryId, AttestationVerdict.ENDORSED, "good work",
                "reviewer-1", TENANCY_ID)).thenReturn(summary);

        var result = service.attest(entryId, "ENDORSED", "good work",
                "reviewer-1", TENANCY_ID);

        assertThat(result.verdict()).isEqualTo("ENDORSED");
        assertThat(result.attestorId()).isEqualTo("reviewer-1");
    }

    @Test
    void attestationsReturnsFromLedgerReader() {
        var entryId = UUID.randomUUID();
        var summary = new AttestationSummary(UUID.randomUUID(), "SOUND",
                "policy", "policy", null, 0.7, Instant.now());
        when(ledgerReader.findAttestationsByEntryId(entryId, TENANCY_ID))
                .thenReturn(List.of(summary));

        var result = service.attestations(entryId, TENANCY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).verdict()).isEqualTo("SOUND");
    }

    @Test
    void requestPeerReviewNoReviewersReturnsAdvisory() {
        var entryId = UUID.randomUUID();
        var entry = ledgerEntryWith("COMMAND", "agent-1", "corr-1", Instant.now());
        when(ledgerReader.findEntryById(entryId, TENANCY_ID))
                .thenReturn(Optional.of(entry));
        when(reviewerProvider.resolve(eq(CHANNEL_ID), any(), eq(entryId), eq(TENANCY_ID)))
                .thenReturn(List.of());

        var result = service.requestPeerReview(entryId, null, CHANNEL_ID,
                "agent-1", TENANCY_ID);

        assertThat(result.reviewersSent()).isZero();
        assertThat(result.advisory()).isNotNull();
    }

    @Test
    void requestPeerReviewRejectsNonCommandEntry() {
        var entryId = UUID.randomUUID();
        var entry = ledgerEntryWith("STATUS", "agent-1", "corr-1", Instant.now());
        when(ledgerReader.findEntryById(entryId, TENANCY_ID))
                .thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.requestPeerReview(entryId, null,
                CHANNEL_ID, "agent-1", TENANCY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMMAND or HANDOFF");
    }

    // ── Helpers ──

    private LedgerEntryView ledgerEntry(String messageType) {
        return new LedgerEntryView(UUID.randomUUID(), 1L, messageType, null,
                "agent-1", null, "content", "corr-1", null, null,
                Instant.now(), null, null, null, null, null, null,
                CHANNEL_ID, null);
    }

    private LedgerEntryView ledgerEntryWith(String messageType,
            String actorId, String correlationId, Instant occurredAt) {
        return new LedgerEntryView(UUID.randomUUID(), 1L, messageType, null,
                actorId, null, "content", correlationId, null, null,
                occurredAt, null, null, null, null, null, null,
                CHANNEL_ID, null);
    }

    private LedgerEntryView eventEntry(String toolName, Long durationMs,
            Long tokenCount) {
        return new LedgerEntryView(UUID.randomUUID(), 1L, "EVENT", null,
                "agent-1", null, null, null, null, null,
                Instant.now(), null, toolName, durationMs, tokenCount,
                null, null, CHANNEL_ID, null);
    }
}
