package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FairnessProperty implements VerificationProperty {

    static final double DEFAULT_GINI_THRESHOLD = 0.5;

    @Inject public MessageLedgerEntryRepository messageRepo;

    @Override
    public String name() {
        return "FAIRNESS";
    }

    @Override
    public String ctlFormula() {
        return "AG(routing_gini ≤ threshold)";
    }

    @Override
    public String description() {
        return "Routing distribution does not concentrate excessively on a single agent.";
    }

    @Override
    public CheckResult check(String tenancyId, Instant from, Instant to) {
        List<MessageLedgerEntry> routingEntries =
                messageRepo.findRoutingEntries(from, to, tenancyId);

        List<MessageLedgerEntry> multiCandidate = routingEntries.stream()
                .filter(e -> e.routingCandidateCount != null && e.routingCandidateCount > 1)
                .toList();

        if (multiCandidate.size() < 2) {
            return new CheckResult(List.of(), 0);
        }

        Map<String, Integer> selectionCounts = new HashMap<>();
        for (MessageLedgerEntry e : multiCandidate) {
            selectionCounts.merge(e.routingSelectedAgent, 1, Integer::sum);
        }

        double gini = computeGini(selectionCounts);
        List<PropertyViolation> violations = new ArrayList<>();

        if (gini > DEFAULT_GINI_THRESHOLD) {
            String topAgent = selectionCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("unknown");
            int topCount = selectionCounts.getOrDefault(topAgent, 0);

            violations.add(new PropertyViolation(
                    name(),
                    "Routing distribution exceeds fairness threshold (Gini="
                            + String.format("%.3f", gini) + " > " + DEFAULT_GINI_THRESHOLD + ")",
                    "topAgent=" + topAgent + " selections=" + topCount
                            + "/" + multiCandidate.size()
                            + " uniqueAgents=" + selectionCounts.size(),
                    to,
                    "MEDIUM"));
        }
        return new CheckResult(violations, 0);
    }

    static double computeGini(Map<String, Integer> counts) {
        if (counts.size() <= 1) return 0.0;
        int n = counts.size();
        int[] values = counts.values().stream().mapToInt(Integer::intValue).sorted().toArray();
        double sum = 0;
        double totalSum = 0;
        for (int i = 0; i < n; i++) {
            sum += (2.0 * (i + 1) - n - 1) * values[i];
            totalSum += values[i];
        }
        if (totalSum == 0) return 0.0;
        return sum / (n * totalSum);
    }
}
