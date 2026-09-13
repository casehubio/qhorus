package io.casehub.qhorus.compliance.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.CommitmentAttestationPolicy.AttestationOutcome;
import io.casehub.qhorus.api.spi.CommitmentContext;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JudgmentCommitmentAttestationPolicyTest {

    private JudgmentCommitmentAttestationPolicy policy;
    private MessageLedgerEntryRepository messageRepo;
    private CurrentPrincipal principal;

    @BeforeEach
    void setUp() {
        policy = new JudgmentCommitmentAttestationPolicy();
        messageRepo = mock(MessageLedgerEntryRepository.class);
        principal = mock(CurrentPrincipal.class);

        policy.messageRepo = messageRepo;
        policy.currentPrincipal = principal;
        policy.doneConfidence = 0.7;
        policy.failureConfidence = 0.6;
        policy.declineConfidence = 0.4;
        policy.responseConfidence = 0.3;
        policy.evidentialChecker = null;

        when(principal.tenancyId()).thenReturn("default");
    }

    @Test
    void doneOnJudgmentCommitmentReturnsEmpty() {
        var ctx = new CommitmentContext("corr-1", UUID.randomUUID(), "ch",
                null, null, null, null, null);
        when(messageRepo.hasJudgmentEvent("corr-1", "default")).thenReturn(true);

        Optional<AttestationOutcome> result =
                policy.attestationFor(MessageType.DONE, "actor-1", ctx);

        assertThat(result).isEmpty();
    }

    @Test
    void doneOnNonJudgmentCommitmentDelegatesToParent() {
        var ctx = new CommitmentContext("corr-2", UUID.randomUUID(), "ch",
                null, null, null, null, null);
        when(messageRepo.hasJudgmentEvent("corr-2", "default")).thenReturn(false);

        Optional<AttestationOutcome> result =
                policy.attestationFor(MessageType.DONE, "actor-1", ctx);

        assertThat(result).isPresent();
        assertThat(result.get().verdict()).isEqualTo(AttestationVerdict.SOUND);
    }

    @Test
    void failureAlwaysDelegatesToParentEvenForJudgmentCommitments() {
        var ctx = new CommitmentContext("corr-3", UUID.randomUUID(), "ch",
                null, null, null, null, null);
        when(messageRepo.hasJudgmentEvent("corr-3", "default")).thenReturn(true);

        Optional<AttestationOutcome> result =
                policy.attestationFor(MessageType.FAILURE, "actor-1", ctx);

        assertThat(result).isPresent();
        assertThat(result.get().verdict()).isEqualTo(AttestationVerdict.FLAGGED);
    }

    @Test
    void declineAlwaysDelegatesToParent() {
        var ctx = new CommitmentContext("corr-4", UUID.randomUUID(), "ch",
                null, null, null, null, null);
        when(messageRepo.hasJudgmentEvent("corr-4", "default")).thenReturn(true);

        Optional<AttestationOutcome> result =
                policy.attestationFor(MessageType.DECLINE, "actor-1", ctx);

        assertThat(result).isPresent();
        assertThat(result.get().verdict()).isEqualTo(AttestationVerdict.FLAGGED);
    }

    @Test
    void nullCorrelationIdDelegatesToParent() {
        var ctx = new CommitmentContext(null, UUID.randomUUID(), "ch",
                null, null, null, null, null);

        Optional<AttestationOutcome> result =
                policy.attestationFor(MessageType.DONE, "actor-1", ctx);

        assertThat(result).isPresent();
        verifyNoInteractions(messageRepo);
    }

    @Test
    void nullContextDelegatesToParent() {
        Optional<AttestationOutcome> result =
                policy.attestationFor(MessageType.DONE, "actor-1", null);

        assertThat(result).isPresent();
        verifyNoInteractions(messageRepo);
    }
}
