package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DeadlockFreedomProperty implements VerificationProperty {

    @Inject
    public CommitmentStore              commitmentStore;
    @Inject
    public MessageLedgerEntryRepository messageRepo;

    @Override
    public String name() {
        return "DEADLOCK_FREEDOM";
    }

    @Override
    public String ctlFormula() {
        return "¬EF(∃ correlationId: delegation_chain_contains_cycle)";
    }

    @Override
    public String description() {
        return "No circular delegation chains — HANDOFF never cycles back to a prior obligor.";
    }

    @Override
    public CheckResult check(String tenancyId, Instant from, Instant to) {
        List<MessageLedgerEntry> handoffs = messageRepo.findHandoffEntries(from, to, tenancyId);

        Set<String>             checkedCorrelations = new HashSet<>();
        List<PropertyViolation> violations          = new ArrayList<>();

        for (MessageLedgerEntry handoff : handoffs) {
            if (handoff.correlationId == null || !checkedCorrelations.add(handoff.correlationId)) {continue;}

            List<Commitment> chain        = commitmentStore.findAllByCorrelationId(handoff.correlationId);
            Set<String>      seenObligors = new HashSet<>();
            for (Commitment link : chain) {
                if (link.obligor() != null && !seenObligors.add(link.obligor())) {
                    violations.add(new PropertyViolation(
                            name(),
                            "Circular delegation detected — obligor '" + link.obligor()
                            + "' appears twice in chain",
                            "correlationId=" + handoff.correlationId
                            + " chainLength=" + chain.size(),
                            link.createdAt(),
                            "HIGH"));
                    break;
                }
            }
        }
        return new CheckResult(violations, 0);
    }
}
