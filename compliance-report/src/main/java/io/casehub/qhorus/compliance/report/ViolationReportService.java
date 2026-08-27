package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.compliance.model.ViolationEntry;
import io.casehub.qhorus.compliance.model.ViolationReport;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ViolationReportService {

    private static final String ENFORCEMENT_SENDER = "system:enforcement";
    private static final String WATCHDOG_SENDER = "system:watchdog";
    private static final int MAX_ENTRIES = 10_000;

    @Inject MessageLedgerEntryRepository ledgerRepo;
    @Inject ChannelStore channelStore;
    @Inject Instance<LedgerVerificationService> verificationServiceInstance;

    public ViolationReport generate(UUID channelId, Instant from, Instant to, String tenancyId) {
        var ch = channelStore.find(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        List<MessageLedgerEntry> enforcementEntries = queryEvents(channelId, ENFORCEMENT_SENDER, from, to, tenancyId);
        List<MessageLedgerEntry> watchdogEntries = queryEvents(channelId, WATCHDOG_SENDER, from, to, tenancyId);

        List<ViolationEntry> violations = new ArrayList<>();
        Map<String, Integer> violationsBySource = new HashMap<>();

        for (MessageLedgerEntry entry : enforcementEntries) {
            violations.add(new ViolationEntry(
                    entry.occurredAt, ENFORCEMENT_SENDER, entry.messageType, "BLOCKING",
                    List.of(), List.of(), "blocked", entry.id));
            violationsBySource.merge("enforcement", 1, Integer::sum);
        }

        for (MessageLedgerEntry entry : watchdogEntries) {
            violations.add(new ViolationEntry(
                    entry.occurredAt, WATCHDOG_SENDER, entry.messageType, "ADVISORY",
                    List.of(), List.of(), "alerted", entry.id));
            violationsBySource.merge("watchdog", 1, Integer::sum);
        }

        String merkleRoot = null;
        if (verificationServiceInstance.isResolvable()) {
            try {
                merkleRoot = verificationServiceInstance.get().treeRoot(channelId, tenancyId);
            } catch (IllegalStateException e) {
                // No Merkle frontier
            }
        }

        return new ViolationReport(
                from, to, channelId, ch.name(),
                violations, enforcementEntries.size(), watchdogEntries.size(), 0,
                violationsBySource, merkleRoot, Instant.now(), 1);
    }

    private List<MessageLedgerEntry> queryEvents(UUID channelId, String sender,
                                                  Instant from, Instant to, String tenancyId) {
        return ledgerRepo.listEntries(channelId, Set.of("EVENT"), null, sender,
                        from, null, false, MAX_ENTRIES, tenancyId).stream()
                .filter(e -> e.occurredAt != null && !e.occurredAt.isAfter(to))
                .toList();
    }
}
