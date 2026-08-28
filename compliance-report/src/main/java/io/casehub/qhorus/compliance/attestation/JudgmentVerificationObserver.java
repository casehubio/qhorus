package io.casehub.qhorus.compliance.attestation;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.judgment.JudgmentEventKinds;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class JudgmentVerificationObserver implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(JudgmentVerificationObserver.class);

    @Inject public LedgerEntryRepository ledger;
    @Inject public MessageLedgerEntryRepository messageRepo;
    @Inject public QhorusConfig config;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (event.messageType() != MessageType.EVENT) return;
        if (event.messageId() == null) return;

        var verifiedEntry = messageRepo.findByMessageId(event.messageId());
        if (verifiedEntry.isEmpty()) return;
        MessageLedgerEntry entry = verifiedEntry.get();
        if (!JudgmentEventKinds.VERIFIED.equals(entry.toolName)) return;

        String tenancyId = event.tenancyId();
        var commandEntry = messageRepo.findLatestByCorrelationId(
                entry.channelId, event.correlationId(), tenancyId);
        if (commandEntry.isEmpty()) return;

        MessageLedgerEntry target = commandEntry.get();
        AttestationVerdict verdict = mapVerdict(entry.verificationOutcome);
        double confidence = mapConfidence(entry.verificationOutcome, entry.evidenceQuality);

        LedgerAttestation attestation = new LedgerAttestation();
        attestation.ledgerEntryId = target.id;
        attestation.subjectId = target.subjectId;
        attestation.attestorId = "system:judgment-verifier";
        attestation.attestorType = ActorType.SYSTEM;
        attestation.verdict = verdict;
        attestation.confidence = confidence;
        attestation.capabilityTag = CapabilityTag.GLOBAL;

        try {
            ledger.saveAttestation(attestation, tenancyId);
            LOG.debugf("Judgment attestation %s/%.2f written for entry %s (correlationId=%s)",
                    verdict, confidence, target.id, event.correlationId());
        } catch (Exception e) {
            LOG.warnf(e, "Failed to write judgment attestation for entry %s", target.id);
        }
    }

    private AttestationVerdict mapVerdict(String verificationOutcome) {
        if ("ACCEPTED".equals(verificationOutcome)) return AttestationVerdict.SOUND;
        return AttestationVerdict.FLAGGED;
    }

    private double mapConfidence(String verificationOutcome, Double evidenceQuality) {
        return switch (verificationOutcome != null ? verificationOutcome : "") {
            case "ACCEPTED" -> Math.max(
                    config.attestation().judgmentAcceptedConfidence(),
                    evidenceQuality != null ? evidenceQuality : 0.7);
            case "REJECTED" -> config.attestation().judgmentRejectedConfidence();
            case "PARTIAL" -> config.attestation().judgmentPartialConfidence();
            default -> config.attestation().judgmentRejectedConfidence();
        };
    }
}
