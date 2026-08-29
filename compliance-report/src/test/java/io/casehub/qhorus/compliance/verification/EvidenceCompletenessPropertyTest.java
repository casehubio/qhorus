package io.casehub.qhorus.compliance.verification;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EvidenceCompletenessPropertyTest {

    private EvidenceCompletenessProperty property;
    private MessageLedgerEntryRepository messageRepo;
    private LedgerEntryRepository ledger;
    private QhorusConfig config;

    @BeforeEach
    void setUp() {
        property = new EvidenceCompletenessProperty();
        messageRepo = mock(MessageLedgerEntryRepository.class);
        ledger = mock(LedgerEntryRepository.class);
        config = mock(QhorusConfig.class, RETURNS_DEEP_STUBS);

        property.messageRepo = messageRepo;
        property.ledger = ledger;
        property.config = config;

        when(config.attestation().judgmentAcceptedConfidence()).thenReturn(0.7);
        when(config.attestation().judgmentRejectedConfidence()).thenReturn(0.3);
        when(config.attestation().judgmentPartialConfidence()).thenReturn(0.5);
    }

    @Test
    void noPendingOrDeferredReturnsEmpty() {
        when(messageRepo.findPendingJudgments(eq("default"))).thenReturn(List.of());
        when(messageRepo.findDoneEntriesWithDeferredAttestation(eq("default")))
                .thenReturn(List.of());

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isTrue();
        assertThat(result.remediationsAvailable()).isZero();
    }

    @Test
    void pendingJudgmentReturnsViolation() {
        var pending = new MessageLedgerEntry();
        pending.judgmentId = UUID.randomUUID();
        pending.correlationId = "corr-pending";
        pending.channelId = UUID.randomUUID();
        pending.occurredAt = Instant.now().minus(2, ChronoUnit.HOURS);

        when(messageRepo.findPendingJudgments(eq("default"))).thenReturn(List.of(pending));
        when(messageRepo.findDoneEntriesWithDeferredAttestation(eq("default")))
                .thenReturn(List.of());

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).description()).contains("YIELDED");
        assertThat(result.violations().get(0).severity()).isEqualTo("HIGH");
    }

    @Test
    void deferredAttestationReturnsViolationWithRemediation() {
        var deferred = new MessageLedgerEntry();
        deferred.id = UUID.randomUUID();
        deferred.correlationId = "corr-deferred";
        deferred.occurredAt = Instant.now().minus(1, ChronoUnit.HOURS);

        when(messageRepo.findPendingJudgments(eq("default"))).thenReturn(List.of());
        when(messageRepo.findDoneEntriesWithDeferredAttestation(eq("default")))
                .thenReturn(List.of(deferred));

        Instant now = Instant.now();
        CheckResult result = property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).severity()).isEqualTo("MEDIUM");
        assertThat(result.remediationsAvailable()).isEqualTo(1);
    }

    @Test
    void checkIsSideEffectFree() {
        when(messageRepo.findPendingJudgments(eq("default"))).thenReturn(List.of());
        when(messageRepo.findDoneEntriesWithDeferredAttestation(eq("default")))
                .thenReturn(List.of());

        Instant now = Instant.now();
        property.check("default", now.minus(7, ChronoUnit.DAYS), now);
        property.check("default", now.minus(7, ChronoUnit.DAYS), now);

        verifyNoInteractions(ledger);
    }

    @Test
    void remediateWritesDualAttestations() {
        UUID   channelId  = UUID.randomUUID();
        UUID   judgmentId = UUID.randomUUID();
        String corrId     = "corr-remediate";

        var doneEntry = new MessageLedgerEntry();
        doneEntry.id            = UUID.randomUUID();
        doneEntry.channelId     = channelId;
        doneEntry.correlationId = corrId;
        doneEntry.judgmentId    = judgmentId;
        doneEntry.subjectId     = UUID.randomUUID();

        var verifiedEntry = new MessageLedgerEntry();
        verifiedEntry.toolName            = "judgment_verified";
        verifiedEntry.verificationOutcome = "ACCEPTED";
        verifiedEntry.evidenceQuality     = 0.85;

        var commandEntry = new MessageLedgerEntry();
        commandEntry.id        = UUID.randomUUID();
        commandEntry.subjectId = doneEntry.subjectId;
        commandEntry.actorId   = "engine";

        when(messageRepo.findDoneEntriesWithDeferredAttestation(eq("default")))
                .thenReturn(List.of(doneEntry));
        when(messageRepo.findJudgmentEvents(isNull(), eq(judgmentId),
                                            isNull(), isNull(), eq("default")))
                .thenReturn(List.of(verifiedEntry));
        when(messageRepo.findLatestByCorrelationId(channelId, corrId, "default"))
                .thenReturn(java.util.Optional.of(commandEntry));
        when(ledger.findAttestationsByEntryId(commandEntry.id, "default"))
                .thenReturn(List.of());

        Instant now = Instant.now();
        int count = property.remediate("default",
                                       now.minus(7, ChronoUnit.DAYS), now);

        assertThat(count).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(
                io.casehub.ledger.runtime.model.LedgerAttestation.class);
        verify(ledger, times(2)).saveAttestation(captor.capture(), eq("default"));

        var attestations = captor.getAllValues();
        assertThat(attestations.get(0).ledgerEntryId).isEqualTo(doneEntry.id);
        assertThat(attestations.get(1).ledgerEntryId).isEqualTo(commandEntry.id);
    }

    @Test
    void remediateSkipsCommandWhenAlreadyAttested() {
        UUID   channelId  = UUID.randomUUID();
        UUID   judgmentId = UUID.randomUUID();
        String corrId     = "corr-guard";

        var doneEntry = new MessageLedgerEntry();
        doneEntry.id            = UUID.randomUUID();
        doneEntry.channelId     = channelId;
        doneEntry.correlationId = corrId;
        doneEntry.judgmentId    = judgmentId;
        doneEntry.subjectId     = UUID.randomUUID();

        var verifiedEntry = new MessageLedgerEntry();
        verifiedEntry.toolName            = "judgment_verified";
        verifiedEntry.verificationOutcome = "REJECTED";

        var commandEntry = new MessageLedgerEntry();
        commandEntry.id        = UUID.randomUUID();
        commandEntry.subjectId = doneEntry.subjectId;

        var existingAttestation = new io.casehub.ledger.runtime.model.LedgerAttestation();
        existingAttestation.attestorId = "system:judgment-verifier";

        when(messageRepo.findDoneEntriesWithDeferredAttestation(eq("default")))
                .thenReturn(List.of(doneEntry));
        when(messageRepo.findJudgmentEvents(isNull(), eq(judgmentId),
                                            isNull(), isNull(), eq("default")))
                .thenReturn(List.of(verifiedEntry));
        when(messageRepo.findLatestByCorrelationId(channelId, corrId, "default"))
                .thenReturn(java.util.Optional.of(commandEntry));
        when(ledger.findAttestationsByEntryId(commandEntry.id, "default"))
                .thenReturn(List.of(existingAttestation));

        Instant now = Instant.now();
        int count = property.remediate("default",
                                       now.minus(7, ChronoUnit.DAYS), now);

        assertThat(count).isEqualTo(1);
        verify(ledger, times(1)).saveAttestation(any(), eq("default"));
    }


}
