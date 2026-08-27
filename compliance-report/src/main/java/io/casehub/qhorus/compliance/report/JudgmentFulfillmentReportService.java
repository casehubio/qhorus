package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.api.judgment.JudgmentEventKinds;
import io.casehub.qhorus.compliance.model.CallerSummary;
import io.casehub.qhorus.compliance.model.JudgmentFulfillmentReport;
import io.casehub.qhorus.compliance.model.JudgmentTypeSummary;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntry;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.OptionalDouble;

@ApplicationScoped
public class JudgmentFulfillmentReportService {

    @Inject MessageLedgerEntryRepository ledgerRepo;
    @Inject Instance<TrustGateService> trustGateServiceInstance;
    @Inject Instance<LedgerVerificationService> verificationServiceInstance;

    public JudgmentFulfillmentReport generate(Instant from, Instant to,
            String judgmentTypeFilter, String actorIdFilter, String tenancyId) {

        List<Object[]> outcomes = ledgerRepo.countJudgmentOutcomes(from, to, tenancyId);

        List<MessageLedgerEntry> pending = ledgerRepo.findPendingJudgments(tenancyId);

        List<MessageLedgerEntry> allEvents = ledgerRepo.findJudgmentEvents(
                null, null, from, to, tenancyId);

        List<MessageLedgerEntry> respondedEvents = allEvents.stream()
                .filter(e -> JudgmentEventKinds.RESPONDED.equals(e.toolName))
                .toList();

        List<MessageLedgerEntry> yieldedEvents = allEvents.stream()
                .filter(e -> JudgmentEventKinds.YIELDED.equals(e.toolName))
                .toList();

        Map<String, TypeAccumulator> byType = new HashMap<>();
        Map<String, CallerAccumulator> byCaller = new HashMap<>();

        for (Object[] row : outcomes) {
            String jType = (String) row[0];
            String toolName = (String) row[1];
            String outcome = (String) row[2];
            long count = (Long) row[3];

            if (judgmentTypeFilter != null && !judgmentTypeFilter.equals(jType)) continue;

            TypeAccumulator ta = byType.computeIfAbsent(jType, k -> new TypeAccumulator());
            if (JudgmentEventKinds.ESCALATED.equals(toolName)) {
                ta.escalated += (int) count;
            } else if ("ACCEPTED".equals(outcome)) {
                ta.accepted += (int) count;
            } else if ("REJECTED".equals(outcome)) {
                ta.rejected += (int) count;
            } else if ("PARTIAL".equals(outcome)) {
                ta.accepted += (int) count;
            }
        }

        for (MessageLedgerEntry e : respondedEvents) {
            if (judgmentTypeFilter != null && !judgmentTypeFilter.equals(e.judgmentType)) continue;
            if (actorIdFilter != null && !actorIdFilter.equals(e.actorId)) continue;

            CallerAccumulator ca = byCaller.computeIfAbsent(e.actorId, k -> new CallerAccumulator());

            if (e.evidenceQuality != null) {
                ca.qualitySum += e.evidenceQuality;
                ca.qualityCount++;
            }

            MessageLedgerEntry yieldedForJudgment = yieldedEvents.stream()
                    .filter(y -> y.judgmentId != null && y.judgmentId.equals(e.judgmentId))
                    .findFirst().orElse(null);
            if (yieldedForJudgment != null && yieldedForJudgment.occurredAt != null && e.occurredAt != null) {
                long responseMs = Duration.between(yieldedForJudgment.occurredAt, e.occurredAt).toMillis();
                ca.responseTimeSum += responseMs;
                ca.responseTimeCount++;

                TypeAccumulator ta = byType.computeIfAbsent(
                        e.judgmentType != null ? e.judgmentType : "unknown", k -> new TypeAccumulator());
                ta.responseTimeSum += responseMs;
                ta.responseTimeCount++;
                if (e.evidenceQuality != null) {
                    ta.qualitySum += e.evidenceQuality;
                    ta.qualityCount++;
                }
            }
        }

        // Per-caller outcome counts: correlate VERIFIED/ESCALATED events to responders via judgmentId
        Map<UUID, String> judgmentToResponder = new HashMap<>();
        for (MessageLedgerEntry e : respondedEvents) {
            if (e.judgmentId != null) {
                judgmentToResponder.put(e.judgmentId, e.actorId);
            }
        }

        List<MessageLedgerEntry> terminalEvents = allEvents.stream()
                .filter(e -> JudgmentEventKinds.VERIFIED.equals(e.toolName)
                        || JudgmentEventKinds.ESCALATED.equals(e.toolName))
                .toList();
        for (MessageLedgerEntry e : terminalEvents) {
            if (e.judgmentId == null) continue;
            String responder = judgmentToResponder.get(e.judgmentId);
            if (responder == null) continue;
            if (actorIdFilter != null && !actorIdFilter.equals(responder)) continue;

            CallerAccumulator ca = byCaller.computeIfAbsent(responder, k -> new CallerAccumulator());
            if (JudgmentEventKinds.ESCALATED.equals(e.toolName)) {
                ca.escalated++;
            } else if ("ACCEPTED".equals(e.verificationOutcome) || "PARTIAL".equals(e.verificationOutcome)) {
                ca.accepted++;
            } else if ("REJECTED".equals(e.verificationOutcome)) {
                ca.rejected++;
            }
        }

        for (MessageLedgerEntry e : pending) {
            if (judgmentTypeFilter != null && !judgmentTypeFilter.equals(e.judgmentType)) continue;

            TypeAccumulator ta = byType.computeIfAbsent(
                    e.judgmentType != null ? e.judgmentType : "unknown", k -> new TypeAccumulator());
            ta.pending++;
        }

        int totalAccepted = 0, totalRejected = 0, totalEscalated = 0, totalPending = 0;
        List<JudgmentTypeSummary> typeSummaries = new ArrayList<>();
        for (var entry : byType.entrySet()) {
            TypeAccumulator ta = entry.getValue();
            int total = ta.accepted + ta.rejected + ta.escalated + ta.pending;
            double rate = total > 0 ? (double) ta.accepted / total : 0;
            double avgResponse = ta.responseTimeCount > 0 ? ta.responseTimeSum / ta.responseTimeCount : 0;
            double avgQuality = ta.qualityCount > 0 ? ta.qualitySum / ta.qualityCount : 0;

            typeSummaries.add(new JudgmentTypeSummary(
                    entry.getKey(), total, ta.accepted, ta.rejected, ta.escalated, ta.pending,
                    rate, avgResponse, avgQuality));

            totalAccepted += ta.accepted;
            totalRejected += ta.rejected;
            totalEscalated += ta.escalated;
            totalPending += ta.pending;
        }

        List<CallerSummary> callerSummaries = new ArrayList<>();
        for (var entry : byCaller.entrySet()) {
            CallerAccumulator ca = entry.getValue();
            Double trustScore = null;
            if (trustGateServiceInstance.isResolvable()) {
                OptionalDouble score = trustGateServiceInstance.get().currentScore(entry.getKey());
                if (score.isPresent()) trustScore = score.getAsDouble();
            }
            double avgResponse = ca.responseTimeCount > 0 ? ca.responseTimeSum / ca.responseTimeCount : 0;
            double avgQuality = ca.qualityCount > 0 ? ca.qualitySum / ca.qualityCount : 0;
            int callerTotal = ca.accepted + ca.rejected + ca.escalated + ca.pending;
            double callerRate = callerTotal > 0 ? (double) ca.accepted / callerTotal : 0;

            callerSummaries.add(new CallerSummary(
                    entry.getKey(), callerTotal, ca.accepted, ca.rejected, ca.escalated, ca.pending,
                    callerRate, avgResponse, avgQuality, trustScore));
        }

        int totalJudgments = totalAccepted + totalRejected + totalEscalated + totalPending;
        double overallRate = totalJudgments > 0 ? (double) totalAccepted / totalJudgments : 0;

        double overallResponseTime = 0;
        double overallQuality = 0;
        long totalResponseCount = 0, totalQualityCount = 0;
        for (TypeAccumulator ta : byType.values()) {
            overallResponseTime += ta.responseTimeSum;
            totalResponseCount += ta.responseTimeCount;
            overallQuality += ta.qualitySum;
            totalQualityCount += ta.qualityCount;
        }

        return new JudgmentFulfillmentReport(
                from, to, typeSummaries, callerSummaries,
                totalJudgments, totalAccepted, totalRejected, totalEscalated, totalPending,
                overallRate,
                totalResponseCount > 0 ? overallResponseTime / totalResponseCount : 0,
                totalQualityCount > 0 ? overallQuality / totalQualityCount : 0,
                null, Instant.now(), 1);
    }

    private static class TypeAccumulator {
        int accepted, rejected, escalated, pending;
        double responseTimeSum, qualitySum;
        int responseTimeCount, qualityCount;
    }

    private static class CallerAccumulator {
        int accepted, rejected, escalated, pending;
        double responseTimeSum, qualitySum;
        int responseTimeCount, qualityCount;
    }
}
