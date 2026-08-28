package io.casehub.qhorus.compliance.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JudgmentVerificationObserverTest {

    private JudgmentVerificationObserver observer;
    private LedgerEntryRepository ledger;
    private MessageLedgerEntryRepository messageRepo;
    private QhorusConfig config;

    @BeforeEach
    void setUp() {
        observer = new JudgmentVerificationObserver();
        ledger = mock(LedgerEntryRepository.class);
        messageRepo = mock(MessageLedgerEntryRepository.class);
        config = mock(QhorusConfig.class, RETURNS_DEEP_STUBS);

        observer.ledger = ledger;
        observer.messageRepo = messageRepo;
        observer.config = config;

        when(config.attestation().judgmentAcceptedConfidence()).thenReturn(0.7);
        when(config.attestation().judgmentRejectedConfidence()).thenReturn(0.3);
        when(config.attestation().judgmentPartialConfidence()).thenReturn(0.5);
    }

    @Test
    void acceptedVerificationWritesSoundAttestation() {
        UUID channelId = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Long messageId = 42L;

        var verifiedEntry = buildEntry(channelId, "judgment_verified",
                "ACCEPTED", 0.85, corrId);
        var commandEntry = buildEntry(channelId, null, null, null, corrId);
        commandEntry.id = UUID.randomUUID();
        commandEntry.subjectId = UUID.randomUUID();

        when(messageRepo.findByMessageId(messageId)).thenReturn(Optional.of(verifiedEntry));
        when(messageRepo.findLatestByCorrelationId(channelId, corrId, "default"))
                .thenReturn(Optional.of(commandEntry));

        observer.onMessage(eventMessage(messageId, channelId, corrId));

        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledger).saveAttestation(captor.capture(), eq("default"));
        var att = captor.getValue();
        assertThat(att.verdict).isEqualTo(AttestationVerdict.SOUND);
        assertThat(att.confidence).isEqualTo(0.85);
        assertThat(att.attestorId).isEqualTo("system:judgment-verifier");
        assertThat(att.attestorType).isEqualTo(ActorType.SYSTEM);
        assertThat(att.ledgerEntryId).isEqualTo(commandEntry.id);
        assertThat(att.subjectId).isEqualTo(commandEntry.subjectId);
    }

    @Test
    void acceptedWithLowEvidenceQualityUsesConfigFloor() {
        UUID channelId = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Long messageId = 43L;

        var verifiedEntry = buildEntry(channelId, "judgment_verified",
                "ACCEPTED", 0.3, corrId);
        var commandEntry = buildEntry(channelId, null, null, null, corrId);
        commandEntry.id = UUID.randomUUID();
        commandEntry.subjectId = UUID.randomUUID();

        when(messageRepo.findByMessageId(messageId)).thenReturn(Optional.of(verifiedEntry));
        when(messageRepo.findLatestByCorrelationId(channelId, corrId, "default"))
                .thenReturn(Optional.of(commandEntry));

        observer.onMessage(eventMessage(messageId, channelId, corrId));

        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledger).saveAttestation(captor.capture(), eq("default"));
        assertThat(captor.getValue().confidence).isEqualTo(0.7);
    }

    @Test
    void rejectedVerificationWritesFlaggedAttestation() {
        UUID channelId = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Long messageId = 44L;

        var verifiedEntry = buildEntry(channelId, "judgment_verified",
                "REJECTED", null, corrId);
        var commandEntry = buildEntry(channelId, null, null, null, corrId);
        commandEntry.id = UUID.randomUUID();
        commandEntry.subjectId = UUID.randomUUID();

        when(messageRepo.findByMessageId(messageId)).thenReturn(Optional.of(verifiedEntry));
        when(messageRepo.findLatestByCorrelationId(channelId, corrId, "default"))
                .thenReturn(Optional.of(commandEntry));

        observer.onMessage(eventMessage(messageId, channelId, corrId));

        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledger).saveAttestation(captor.capture(), eq("default"));
        assertThat(captor.getValue().verdict).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(captor.getValue().confidence).isEqualTo(0.3);
    }

    @Test
    void partialVerificationWritesFlaggedWithMediumConfidence() {
        UUID channelId = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Long messageId = 45L;

        var verifiedEntry = buildEntry(channelId, "judgment_verified",
                "PARTIAL", 0.6, corrId);
        var commandEntry = buildEntry(channelId, null, null, null, corrId);
        commandEntry.id = UUID.randomUUID();
        commandEntry.subjectId = UUID.randomUUID();

        when(messageRepo.findByMessageId(messageId)).thenReturn(Optional.of(verifiedEntry));
        when(messageRepo.findLatestByCorrelationId(channelId, corrId, "default"))
                .thenReturn(Optional.of(commandEntry));

        observer.onMessage(eventMessage(messageId, channelId, corrId));

        var captor = ArgumentCaptor.forClass(LedgerAttestation.class);
        verify(ledger).saveAttestation(captor.capture(), eq("default"));
        assertThat(captor.getValue().verdict).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(captor.getValue().confidence).isEqualTo(0.5);
    }

    @Test
    void nonEventMessageIgnored() {
        observer.onMessage(new MessageReceivedEvent(1L, "ch", UUID.randomUUID(),
                "default", MessageType.COMMAND, "agent", null, ActorType.AGENT,
                null, Instant.now(), "hello", null, null));
        verifyNoInteractions(ledger);
        verifyNoInteractions(messageRepo);
    }

    @Test
    void nullMessageIdIgnored() {
        observer.onMessage(new MessageReceivedEvent(null, "ch", UUID.randomUUID(),
                "default", MessageType.EVENT, "engine", null, ActorType.SYSTEM,
                "corr", Instant.now(), null, null, null));
        verifyNoInteractions(ledger);
        verifyNoInteractions(messageRepo);
    }

    @Test
    void nonVerifiedEventIgnored() {
        Long messageId = 46L;
        var entry = buildEntry(UUID.randomUUID(), "judgment_yielded",
                null, null, "corr");
        when(messageRepo.findByMessageId(messageId)).thenReturn(Optional.of(entry));

        observer.onMessage(eventMessage(messageId, UUID.randomUUID(), "corr"));
        verifyNoInteractions(ledger);
    }

    @Test
    void missingCommandEntrySkipsSilently() {
        UUID channelId = UUID.randomUUID();
        String corrId = UUID.randomUUID().toString();
        Long messageId = 47L;

        var verifiedEntry = buildEntry(channelId, "judgment_verified",
                "ACCEPTED", 0.9, corrId);
        when(messageRepo.findByMessageId(messageId)).thenReturn(Optional.of(verifiedEntry));
        when(messageRepo.findLatestByCorrelationId(channelId, corrId, "default"))
                .thenReturn(Optional.empty());

        observer.onMessage(eventMessage(messageId, channelId, corrId));
        verifyNoInteractions(ledger);
    }

    private MessageReceivedEvent eventMessage(Long messageId, UUID channelId, String corrId) {
        return new MessageReceivedEvent(messageId, "ch", channelId,
                "default", MessageType.EVENT, "engine", null, ActorType.SYSTEM,
                corrId, Instant.now(), null, null, null);
    }

    private MessageLedgerEntry buildEntry(UUID channelId, String toolName,
            String verificationOutcome, Double evidenceQuality, String corrId) {
        var entry = new MessageLedgerEntry();
        entry.channelId = channelId;
        entry.toolName = toolName;
        entry.verificationOutcome = verificationOutcome;
        entry.evidenceQuality = evidenceQuality;
        entry.correlationId = corrId;
        entry.tenancyId = "default";
        return entry;
    }
}
