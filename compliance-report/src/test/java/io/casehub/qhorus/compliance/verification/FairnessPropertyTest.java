package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FairnessPropertyTest {

    private FairnessProperty property;
    private MessageLedgerEntryRepository messageRepo;

    @BeforeEach
    void setUp() {
        property = new FairnessProperty();
        messageRepo = mock(MessageLedgerEntryRepository.class);
        property.messageRepo = messageRepo;
    }

    @Test
    void uniformDistributionPasses() {
        List<MessageLedgerEntry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(routingEntry("agent-" + (i % 5), 5));
        }
        when(messageRepo.findRoutingEntries(any(), any(), eq("default")))
                .thenReturn(entries);

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void concentratedDistributionFails() {
        List<MessageLedgerEntry> entries = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            entries.add(routingEntry("agent-dominant", 3));
        }
        entries.add(routingEntry("agent-b", 3));
        entries.add(routingEntry("agent-c", 3));
        when(messageRepo.findRoutingEntries(any(), any(), eq("default")))
                .thenReturn(entries);

        Instant     now    = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations().get(0).description()).contains("Gini");
    }

    @Test
    void singleCandidateEntriesExcluded() {
        List<MessageLedgerEntry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(routingEntry("agent-only", 1));
        }
        when(messageRepo.findRoutingEntries(any(), any(), eq("default")))
                .thenReturn(entries);

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void tooFewEntriesPasses() {
        when(messageRepo.findRoutingEntries(any(), any(), eq("default")))
                .thenReturn(List.of(routingEntry("agent-a", 3)));

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void giniComputationPerfectEquality() {
        assertThat(FairnessProperty.computeGini(
                Map.of("a", 10, "b", 10, "c", 10))).isCloseTo(0.0, within(0.01));
    }

    @Test
    void giniComputationHighInequality() {
        assertThat(FairnessProperty.computeGini(
                Map.of("a", 100, "b", 1, "c", 1))).isGreaterThan(0.5);
    }

    private MessageLedgerEntry routingEntry(String selectedAgent, int candidateCount) {
        var entry = new MessageLedgerEntry();
        entry.routingSelectedAgent = selectedAgent;
        entry.routingCandidateCount = candidateCount;
        return entry;
    }
}
