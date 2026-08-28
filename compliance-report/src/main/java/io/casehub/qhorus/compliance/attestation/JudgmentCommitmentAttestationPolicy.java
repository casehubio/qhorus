package io.casehub.qhorus.compliance.attestation;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.CommitmentContext;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.qhorus.runtime.ledger.StoredCommitmentAttestationPolicy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class JudgmentCommitmentAttestationPolicy extends StoredCommitmentAttestationPolicy {

    @Inject
    public MessageLedgerEntryRepository messageRepo;

    @Inject
    public CurrentPrincipal currentPrincipal;

    @Override
    public Optional<AttestationOutcome> attestationFor(final MessageType terminalType,
            final String resolvedActorId, final CommitmentContext context) {
        if (terminalType == MessageType.DONE && isJudgmentCommitment(context)) {
            return Optional.empty();
        }
        return super.attestationFor(terminalType, resolvedActorId, context);
    }

    private boolean isJudgmentCommitment(final CommitmentContext ctx) {
        if (ctx == null || ctx.correlationId() == null) return false;
        String tenancyId = currentPrincipal.tenancyId();
        return messageRepo.hasJudgmentEvent(ctx.correlationId(), tenancyId);
    }
}
