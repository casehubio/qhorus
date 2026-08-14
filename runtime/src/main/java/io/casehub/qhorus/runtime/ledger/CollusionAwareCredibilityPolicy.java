package io.casehub.qhorus.runtime.ledger;

import io.casehub.ledger.api.model.CredibilityFlag;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.spi.LedgerEntryRepository;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class CollusionAwareCredibilityPolicy implements AttestorCredibilityPolicy {

    private final AgreementCredibilityPolicy base;
    private final LedgerEntryRepository ledger;
    private final double collusionThreshold;

    public CollusionAwareCredibilityPolicy(AgreementCredibilityPolicy base,
                                            LedgerEntryRepository ledger,
                                            double collusionThreshold) {
        this.base = base;
        this.ledger = ledger;
        this.collusionThreshold = collusionThreshold;
    }

    @Override
    public CredibilityAssessment assess(String attestorId) {
        return assessBatch(Set.of(attestorId)).getOrDefault(attestorId, CredibilityAssessment.NEUTRAL);
    }

    @Override
    public Map<String, CredibilityAssessment> assessBatch(Set<String> attestorIds) {
        Map<String, CredibilityAssessment> baseResults = base.assessBatch(attestorIds);
        Map<String, Map<String, Long>> pairCounts =
                ledger.findPeerAttestationPairCounts(attestorIds, null);

        Map<String, CredibilityAssessment> result = new LinkedHashMap<>();
        for (Map.Entry<String, CredibilityAssessment> entry : baseResults.entrySet()) {
            String attestorId = entry.getKey();
            CredibilityAssessment baseAssessment = entry.getValue();

            Map<String, Long> pairs = pairCounts.getOrDefault(attestorId, Map.of());
            boolean collusionSuspect = detectCollusion(pairs);

            if (collusionSuspect) {
                Set<CredibilityFlag> flags = EnumSet.copyOf(baseAssessment.flags().isEmpty()
                        ? EnumSet.noneOf(CredibilityFlag.class)
                        : baseAssessment.flags());
                flags.add(CredibilityFlag.COLLUSION_SUSPECT);
                result.put(attestorId, new CredibilityAssessment(
                        baseAssessment.weight(),
                        baseAssessment.reason() != null
                                ? baseAssessment.reason() + "; collusion suspect"
                                : "collusion suspect",
                        flags));
            } else {
                result.put(attestorId, baseAssessment);
            }
        }
        return result;
    }

    private boolean detectCollusion(Map<String, Long> pairsForAttestor) {
        if (pairsForAttestor.isEmpty()) {
            return false;
        }

        long totalEndorsements = pairsForAttestor.values().stream().mapToLong(Long::longValue).sum();
        if (totalEndorsements == 0) {
            return false;
        }

        for (Long mutualCount : pairsForAttestor.values()) {
            double ratio = (double) mutualCount / totalEndorsements;
            if (ratio >= collusionThreshold) {
                return true;
            }
        }
        return false;
    }
}
