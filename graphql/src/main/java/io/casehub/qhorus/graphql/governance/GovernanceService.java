package io.casehub.qhorus.graphql.governance;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentPage;
import io.casehub.qhorus.api.message.CommitmentQuery;
import io.casehub.qhorus.api.spi.governance.GovernanceApi;
import io.casehub.qhorus.api.store.CommitmentReader;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.store.query.WatchdogQuery;
import io.casehub.qhorus.api.watchdog.Watchdog;
import io.casehub.qhorus.api.watchdog.WatchdogAction;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GovernanceService implements GovernanceApi {

    private final CommitmentReader commitmentReader;
    private final WatchdogStore watchdogStore;

    public GovernanceService(CommitmentReader commitmentReader, WatchdogStore watchdogStore) {
        this.commitmentReader = commitmentReader;
        this.watchdogStore = watchdogStore;
    }

    @Override
    public CommitmentPage commitments(CommitmentQuery query) {
        int offset = query != null && query.offset() != null ? query.offset() : 0;
        int limit = query != null && query.limit() != null ? query.limit() : 20;

        List<Commitment> all = resolveCommitments(query);
        int total = all.size();
        int end = Math.min(offset + limit, total);
        List<Commitment> items = offset < total
                ? all.subList(offset, end)
                : List.of();

        boolean hasNext = end < total;
        return new CommitmentPage(items, hasNext, null);
    }


    @Override
    public List<Commitment> pendingCommitments() {
        return commitmentReader.findAllOpen();
    }

    @Override
    public List<Commitment> myCommitments(UUID channelId, String sender, String role) {
        String r = role == null ? "both" : role.toLowerCase();
        return switch (r) {
            case "obligor" -> commitmentReader.findOpenByObligor(sender, channelId);
            case "requester" -> commitmentReader.findOpenByRequester(sender, channelId);
            default -> {
                var list = new ArrayList<>(commitmentReader.findOpenByObligor(sender, channelId));
                list.addAll(commitmentReader.findOpenByRequester(sender, channelId));
                list.sort(Comparator.comparing(Commitment::createdAt));
                yield list;
            }
        };
    }

    @Override
    public Commitment commitment(String correlationId) {
        return commitmentReader.findByCorrelationId(correlationId)
                               .orElseThrow(() -> new IllegalArgumentException(
                                       "No commitment found for correlation_id=" + correlationId));
    }

    @Override
    public List<Watchdog> watchdogs() {
        return watchdogStore.scan(WatchdogQuery.all());
    }

    @Override
    public Watchdog registerWatchdog(String conditionType, String targetName,
                                     Integer thresholdSeconds, Integer thresholdCount,
                                     Integer similarityPct, String notificationChannel,
                                     String createdBy, String action) {
        WatchdogConditionType type = WatchdogConditionType.fromString(conditionType)
                                                          .orElseThrow(() -> new IllegalArgumentException(
                                                                  "Unknown condition_type: " + conditionType));
        WatchdogAction parsedAction = WatchdogAction.ALERT;
        if (action != null && !action.isBlank()) {
            parsedAction = WatchdogAction.valueOf(action);
        }
        return watchdogStore.put(Watchdog.builder(type, targetName)
                                         .thresholdSeconds(thresholdSeconds).thresholdCount(thresholdCount)
                                         .similarityPct(similarityPct)
                                         .notificationChannel(notificationChannel).createdBy(createdBy)
                                         .action(parsedAction).build());
    }

    @Override
    public boolean deleteWatchdog(UUID watchdogId) {
        boolean found = watchdogStore.find(watchdogId).isPresent();
        if (found) {
            watchdogStore.delete(watchdogId);
        }
        return found;
    }

    private List<Commitment> resolveCommitments(CommitmentQuery query) {
        if (query == null) {
            return commitmentReader.findAllOpen();
        }
        if (query.channelId() != null && query.state() != null) {
            return commitmentReader.findByState(query.state(), query.channelId());
        }
        if (query.channelId() != null && query.obligor() != null) {
            return commitmentReader.findOpenByObligor(query.obligor(), query.channelId());
        }
        if (query.channelId() != null && query.requester() != null) {
            return commitmentReader.findOpenByRequester(query.requester(), query.channelId());
        }
        if (query.channelId() != null) {
            return commitmentReader.findByChannel(query.channelId());
        }
        if (query.obligor() != null) {
            return commitmentReader.findOpenByObligor(query.obligor());
        }
        return commitmentReader.findAllOpen();
    }
}
