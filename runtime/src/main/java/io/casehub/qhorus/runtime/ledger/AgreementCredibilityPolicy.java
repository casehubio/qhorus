package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CredibilityFlag;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;

import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@DefaultBean
@ApplicationScoped
public class AgreementCredibilityPolicy implements AttestorCredibilityPolicy {

    private static final Set<AttestationVerdict> PEER_VERDICTS =
            Set.of(AttestationVerdict.ENDORSED, AttestationVerdict.CHALLENGED);
    private static final Set<AttestationVerdict> POLICY_VERDICTS =
            Set.of(AttestationVerdict.SOUND, AttestationVerdict.FLAGGED);

    @Inject
    LedgerEntryRepository ledger;

    int minDataPoints = 5;
    double lowAgreementThreshold = 0.3;

    @Inject
    void configure(io.casehub.qhorus.runtime.config.QhorusConfig config) {
        this.minDataPoints = config.attestation().credibilityMinDataPoints();
        this.lowAgreementThreshold = config.attestation().credibilityLowAgreementThreshold();
    }

    @Override
    public AttestorCredibilityPolicy.CredibilityAssessment assess(String attestorId) {
        return assessBatch(Set.of(attestorId)).getOrDefault(attestorId, AttestorCredibilityPolicy.CredibilityAssessment.NEUTRAL);
    }

    @Override
    public Map<String, AttestorCredibilityPolicy.CredibilityAssessment> assessBatch(Set<String> attestorIds) {
        if (attestorIds == null || attestorIds.isEmpty()) {
            return Map.of();
        }

        List<LedgerAttestation> peerAttestations =
                ledger.findPeerAttestationsByAttestorIds(attestorIds, null);

        Map<String, List<LedgerAttestation>> byAttestor = peerAttestations.stream()
                .collect(Collectors.groupingBy(a -> a.attestorId));

        Map<String, AttestorCredibilityPolicy.CredibilityAssessment> result = new LinkedHashMap<>();
        for (String attestorId : attestorIds) {
            List<LedgerAttestation> attestorPeerAttestations = byAttestor.getOrDefault(attestorId, List.of());
            result.put(attestorId, computeCredibility(attestorId, attestorPeerAttestations));
        }
        return result;
    }

    private AttestorCredibilityPolicy.CredibilityAssessment computeCredibility(String attestorId,
                                                      List<LedgerAttestation> peerAttestations) {
        double alpha = 1.0;
        double beta = 1.0;
        int dataPoints = 0;

        Set<UUID> processedEntries = new java.util.HashSet<>();

        for (LedgerAttestation peerAtt : peerAttestations) {
            if (!processedEntries.add(peerAtt.ledgerEntryId)) {
                continue;
            }

            List<LedgerAttestation> entryAttestations =
                    ledger.findAttestationsByEntryId(peerAtt.ledgerEntryId, null);

            LedgerAttestation policyAtt = null;
            for (LedgerAttestation att : entryAttestations) {
                if (POLICY_VERDICTS.contains(att.verdict) && !att.attestorId.equals(attestorId)) {
                    policyAtt = att;
                    break;
                }
            }

            if (policyAtt == null) {
                continue;
            }

            dataPoints++;
            boolean agrees = isAgreement(peerAtt.verdict, policyAtt.verdict);
            if (agrees) {
                alpha += 1.0;
            } else {
                beta += 1.0;
            }
        }

        if (dataPoints < minDataPoints) {
            return new AttestorCredibilityPolicy.CredibilityAssessment(1.0, "insufficient data (" + dataPoints + "/" + minDataPoints + ")",
                    Set.of(CredibilityFlag.INSUFFICIENT_DATA));
        }

        double score = alpha / (alpha + beta);

        Set<CredibilityFlag> flags = EnumSet.noneOf(CredibilityFlag.class);
        if (score < lowAgreementThreshold) {
            flags.add(CredibilityFlag.LOW_AGREEMENT);
        }

        String reason = String.format("agreement=%.2f (%d/%d agree, %d data points)",
                score, (int) (alpha - 1.0), dataPoints, dataPoints);

        return new AttestorCredibilityPolicy.CredibilityAssessment(score, reason, flags);
    }

    private static boolean isAgreement(AttestationVerdict peerVerdict, AttestationVerdict policyVerdict) {
        return (peerVerdict == AttestationVerdict.ENDORSED && policyVerdict == AttestationVerdict.SOUND)
               || (peerVerdict == AttestationVerdict.CHALLENGED && policyVerdict == AttestationVerdict.FLAGGED);
    }
}
