package io.casehub.qhorus.api.audit;

import io.casehub.ledger.api.model.AttestationVerdict;

import java.util.UUID;

public interface PeerAttestor {

    AttestationSummary write(UUID entryId, AttestationVerdict verdict,
            String evidence, String attestorId, String tenancyId);
}
