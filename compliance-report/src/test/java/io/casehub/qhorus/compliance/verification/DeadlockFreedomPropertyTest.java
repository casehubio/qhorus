package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeadlockFreedomPropertyTest {

    private DeadlockFreedomProperty      property;
    private CommitmentStore              store;
    private MessageLedgerEntryRepository messageRepo;

    @BeforeEach
    void setUp() {
        property                 = new DeadlockFreedomProperty();
        store                    = mock(CommitmentStore.class);
        messageRepo              = mock(MessageLedgerEntryRepository.class);
        property.commitmentStore = store;
        property.messageRepo     = messageRepo;
    }

    @Test
    void noHandoffsReturnsEmpty() {
        when(messageRepo.findHandoffEntries(any(), any(), eq("default"))).thenReturn(List.of());

        Instant     now    = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
    }

    @Test
    void circularDelegationDetected() {
        String corrId       = "corr-cycle";
        var    handoffEntry = new MessageLedgerEntry();
        handoffEntry.correlationId = corrId;

        when(messageRepo.findHandoffEntries(any(), any(), eq("default")))
                .thenReturn(List.of(handoffEntry));

        Commitment hop0 = Commitment.builder()
                                    .id(UUID.randomUUID()).correlationId(corrId).channelId(UUID.randomUUID())
                                    .messageType(MessageType.COMMAND).requester("engine").obligor("agent-a")
                                    .state(CommitmentState.DELEGATED)
                                    .createdAt(Instant.now().minus(2, ChronoUnit.HOURS)).build();
        Commitment hop1 = Commitment.builder()
                                    .id(UUID.randomUUID()).correlationId(corrId).channelId(UUID.randomUUID())
                                    .messageType(MessageType.COMMAND).requester("engine").obligor("agent-b")
                                    .state(CommitmentState.DELEGATED)
                                    .createdAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        Commitment hop2 = Commitment.builder()
                                    .id(UUID.randomUUID()).correlationId(corrId).channelId(UUID.randomUUID())
                                    .messageType(MessageType.COMMAND).requester("engine").obligor("agent-a")
                                    .state(CommitmentState.OPEN)
                                    .createdAt(Instant.now()).build();

        when(store.findAllByCorrelationId(corrId)).thenReturn(List.of(hop0, hop1, hop2));

        Instant     now    = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).evidence()).contains("corr-cycle");
        assertThat(result.violations().get(0).description()).contains("agent-a");
    }

    @Test
    void linearDelegationPasses() {
        String corrId       = "corr-linear";
        var    handoffEntry = new MessageLedgerEntry();
        handoffEntry.correlationId = corrId;

        when(messageRepo.findHandoffEntries(any(), any(), eq("default")))
                .thenReturn(List.of(handoffEntry));

        Commitment hop0 = Commitment.builder()
                                    .id(UUID.randomUUID()).correlationId(corrId).channelId(UUID.randomUUID())
                                    .messageType(MessageType.COMMAND).requester("engine").obligor("agent-a")
                                    .state(CommitmentState.DELEGATED)
                                    .createdAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        Commitment hop1 = Commitment.builder()
                                    .id(UUID.randomUUID()).correlationId(corrId).channelId(UUID.randomUUID())
                                    .messageType(MessageType.COMMAND).requester("engine").obligor("agent-b")
                                    .state(CommitmentState.OPEN)
                                    .createdAt(Instant.now()).build();

        when(store.findAllByCorrelationId(corrId)).thenReturn(List.of(hop0, hop1));

        Instant     now    = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
    }
}
