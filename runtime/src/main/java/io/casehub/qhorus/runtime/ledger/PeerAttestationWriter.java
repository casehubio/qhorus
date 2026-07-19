package io.casehub.qhorus.runtime.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class PeerAttestationWriter {

    private static final Logger LOG = Logger.getLogger(PeerAttestationWriter.class);
    private static final Set<AttestationVerdict> PEER_VERDICTS =
            Set.of(AttestationVerdict.ENDORSED, AttestationVerdict.CHALLENGED);
    private static final Set<String> ATTESTABLE_TYPES = Set.of("COMMAND", "HANDOFF");

    @Inject
    public LedgerEntryRepository ledger;

    @Inject
    public InstanceStore instanceStore;

    @Inject
    public QhorusConfig config;

    @Inject
    public ObjectMapper objectMapper;

    public LedgerAttestation write(UUID ledgerEntryId, AttestationVerdict verdict,
                            String evidence, String attestorId, String tenancyId) {
        if (!PEER_VERDICTS.contains(verdict)) {
            throw new IllegalArgumentException(
                    "Peer attestation verdict must be ENDORSED or CHALLENGED, not " + verdict);
        }

        LedgerEntry entry = ledger.findEntryById(ledgerEntryId, tenancyId)
                                  .orElseThrow(() -> new IllegalArgumentException(
                                          "Ledger entry not found: " + ledgerEntryId));

        if (!(entry instanceof MessageLedgerEntry msgEntry)) {
            throw new IllegalArgumentException(
                    "Ledger entry " + ledgerEntryId + " is not a message entry");
        }

        if (!ATTESTABLE_TYPES.contains(msgEntry.messageType)) {
            throw new IllegalArgumentException(
                    "Peer attestation requires a COMMAND or HANDOFF entry, not " + msgEntry.messageType);
        }

        if (attestorId.equals(msgEntry.actorId)) {
            throw new IllegalArgumentException(
                    "Self-attestation is not permitted — attestor and entry actor are both '" + attestorId + "'");
        }

        if (instanceStore.findByInstanceId(attestorId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Attestor '" + attestorId + "' is not a registered instance");
        }

        double confidence = verdict == AttestationVerdict.ENDORSED
                            ? config.attestation().peerEndorsedConfidence()
                            : config.attestation().peerChallengedConfidence();

        String capabilityTag = extractCapabilityTag(msgEntry.content);

        LedgerAttestation attestation = new LedgerAttestation();
        attestation.ledgerEntryId = msgEntry.id;
        attestation.subjectId     = msgEntry.subjectId;
        attestation.attestorId    = attestorId;
        attestation.attestorType  = ActorType.AGENT;
        attestation.attestorRole  = "peer-reviewer";
        attestation.verdict       = verdict;
        attestation.evidence      = evidence;
        attestation.confidence    = confidence;
        attestation.capabilityTag = capabilityTag;

        ledger.saveAttestation(attestation, tenancyId);
        LOG.debugf("Peer attestation %s written for entry %s by %s (capability='%s')",
                   verdict, ledgerEntryId, attestorId, capabilityTag);
        return attestation;}

    private String extractCapabilityTag(String content) {
        if (content == null || !content.stripLeading().startsWith("{")) {
            return CapabilityTag.GLOBAL;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode cap = root.get("capability");
            if (cap != null && cap.isTextual() && !cap.asText().isBlank()) {
                return cap.asText();
            }
        } catch (Exception ignored) {
        }
        return CapabilityTag.GLOBAL;
    }
}
