package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class SafetyProperty implements VerificationProperty {

    @Inject public MessageLedgerEntryRepository messageRepo;

    @Override
    public String name() {
        return "SAFETY";
    }

    @Override
    public String ctlFormula() {
        return "AG(FULFILLED → attestation_exists)";
    }

    @Override
    public String description() {
        return "Every fulfilled commitment has an attestation — no unattested DONE.";
    }

    @Override
    public CheckResult check(String tenancyId, Instant from, Instant to) {
        List<MessageLedgerEntry> unattested =
                messageRepo.findDoneEntriesWithoutAttestation(from, to, tenancyId);
        List<PropertyViolation> violations = unattested.stream()
                .map(e -> new PropertyViolation(
                        name(),
                        "DONE entry has no attestation",
                        "entryId=" + e.id + " correlationId=" + e.correlationId
                                + " channelId=" + e.channelId,
                        e.occurredAt,
                        "HIGH"))
                .toList();
        return new CheckResult(violations, 0);
    }
}
