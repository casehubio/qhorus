package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.CommitmentAttestationPolicy;
import io.casehub.qhorus.api.spi.CommitmentContext;
import io.casehub.qhorus.runtime.audit.EvidentialChecker;

import java.util.Optional;

public class StoredCommitmentAttestationPolicy implements CommitmentAttestationPolicy {

    private final double doneConfidence;
    private final double failureConfidence;
    private final double declineConfidence;
    private final double responseConfidence;
    private final EvidentialChecker evidentialChecker;

    public StoredCommitmentAttestationPolicy(double doneConfidence, double failureConfidence,
                                              double declineConfidence, double responseConfidence,
                                              EvidentialChecker evidentialChecker) {
        this.doneConfidence = doneConfidence;
        this.failureConfidence = failureConfidence;
        this.declineConfidence = declineConfidence;
        this.responseConfidence = responseConfidence;
        this.evidentialChecker = evidentialChecker;
    }

    @Override
    public Optional<AttestationOutcome> attestationFor(final MessageType terminalType,
            final String resolvedActorId, final CommitmentContext context) {
        return switch (terminalType) {
            case DONE -> {
                AttestationVerdict verdict = AttestationVerdict.SOUND;
                if (evidentialChecker != null && context != null) {
                    var violations = evidentialChecker.checkObligation(
                            terminalType.name(), context);
                    if (!violations.isEmpty()) {
                        verdict = AttestationVerdict.FLAGGED;
                    }
                }
                yield Optional.of(new AttestationOutcome(
                        verdict,
                        doneConfidence,
                        resolvedActorId,
                        ActorType.AGENT));
            }
            case FAILURE -> Optional.of(new AttestationOutcome(
                    AttestationVerdict.FLAGGED,
                    failureConfidence,
                    "system",
                    ActorType.SYSTEM));
            case DECLINE -> Optional.of(new AttestationOutcome(
                    AttestationVerdict.FLAGGED,
                    declineConfidence,
                    "system",
                    ActorType.SYSTEM));
            case RESPONSE -> Optional.of(new AttestationOutcome(
                    AttestationVerdict.FLAGGED,
                    responseConfidence,
                    "system",
                    ActorType.SYSTEM));
            default -> Optional.empty();
        };
    }
}
