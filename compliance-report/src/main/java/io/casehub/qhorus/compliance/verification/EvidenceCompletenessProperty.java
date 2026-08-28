package io.casehub.qhorus.compliance.verification;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EvidenceCompletenessProperty implements RemediatingProperty {

    private static final Logger LOG = Logger.getLogger(EvidenceCompletenessProperty.class);

    @Inject public MessageLedgerEntryRepository messageRepo;
    @Inject public LedgerEntryRepository ledger;
    @Inject public QhorusConfig config;

    @Override
    public String name() {
        return "EVIDENCE_COMPLETENESS";
    }

    @Override
    public String ctlFormula() {
        return "AG(YIELDED → AF(VERIFIED ∨ ESCALATED))";
    }

    @Override
    public String description() {
        return "Every judgment yield eventually reaches verification or escalation, "
                + "and every deferred attestation is eventually written.";
    }

    @Override
    public CheckResult check(String tenancyId, Instant from, Instant to) {
        List<PropertyViolation> violations = new ArrayList<>();

        List<MessageLedgerEntry> pending = messageRepo.findPendingJudgments(tenancyId);
        for (MessageLedgerEntry e : pending) {
            violations.add(new PropertyViolation(
                    name(),
                    "Judgment YIELDED with no VERIFIED or ESCALATED event",
                    "judgmentId=" + e.judgmentId + " correlationId=" + e.correlationId
                            + " channelId=" + e.channelId,
                    e.occurredAt,
                    "HIGH"));
        }

        List<MessageLedgerEntry> deferred =
                messageRepo.findDoneEntriesWithDeferredAttestation(tenancyId);
        int remediationsAvailable = deferred.size();
        for (MessageLedgerEntry e : deferred) {
            violations.add(new PropertyViolation(
                    name(),
                    "Judgment DONE has VERIFIED event but no attestation — deferred attestation missing",
                    "entryId=" + e.id + " correlationId=" + e.correlationId,
                    e.occurredAt,
                    "MEDIUM"));
        }

        return new CheckResult(violations, remediationsAvailable);
    }

    @Override
    public int remediate(String tenancyId, Instant from, Instant to) {
        List<MessageLedgerEntry> deferred =
                messageRepo.findDoneEntriesWithDeferredAttestation(tenancyId);
        int count = 0;
        for (MessageLedgerEntry doneEntry : deferred) {
            var verifiedEntries = messageRepo.findJudgmentEvents(
                    null, doneEntry.judgmentId, null, null, tenancyId);
            var verified = verifiedEntries.stream()
                    .filter(e -> "judgment_verified".equals(e.toolName))
                    .findFirst();
            if (verified.isEmpty()) continue;

            MessageLedgerEntry v = verified.get();
            AttestationVerdict verdict = "ACCEPTED".equals(v.verificationOutcome)
                    ? AttestationVerdict.SOUND : AttestationVerdict.FLAGGED;
            double confidence = switch (v.verificationOutcome != null ? v.verificationOutcome : "") {
                case "ACCEPTED" -> Math.max(
                        config.attestation().judgmentAcceptedConfidence(),
                        v.evidenceQuality != null ? v.evidenceQuality : 0.7);
                case "REJECTED" -> config.attestation().judgmentRejectedConfidence();
                case "PARTIAL" -> config.attestation().judgmentPartialConfidence();
                default -> config.attestation().judgmentRejectedConfidence();
            };

            LedgerAttestation attestation = new LedgerAttestation();
            attestation.ledgerEntryId = doneEntry.id;
            attestation.subjectId = doneEntry.subjectId;
            attestation.attestorId = "system:judgment-verifier";
            attestation.attestorType = ActorType.SYSTEM;
            attestation.verdict = verdict;
            attestation.confidence = confidence;
            attestation.capabilityTag = CapabilityTag.GLOBAL;

            try {
                ledger.saveAttestation(attestation, tenancyId);
                count++;
                LOG.infof("Remediated missing judgment attestation for entry %s", doneEntry.id);
            } catch (Exception e) {
                LOG.warnf(e, "Failed to remediate judgment attestation for entry %s", doneEntry.id);
            }
        }
        return count;
    }
}
