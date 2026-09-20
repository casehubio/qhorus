package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.qhorus.api.audit.AttestationSummary;
import io.casehub.qhorus.api.audit.PeerAttestor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class PeerAttestorAdapter implements PeerAttestor {

    @Inject
    PeerAttestationWriter writer;

    @Override
    public AttestationSummary write(UUID entryId, AttestationVerdict verdict,
            String evidence, String attestorId, String tenancyId) {
        var attestation = writer.write(entryId, verdict, evidence, attestorId, tenancyId);
        return new AttestationSummary(
                attestation.id,
                attestation.verdict != null ? attestation.verdict.name() : null,
                attestation.attestorId,
                attestation.attestorRole,
                attestation.evidence,
                attestation.confidence,
                attestation.occurredAt);
    }
}
