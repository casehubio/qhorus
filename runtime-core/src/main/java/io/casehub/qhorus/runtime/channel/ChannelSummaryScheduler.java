package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.channel.ChannelSummary;
import io.casehub.qhorus.api.channel.ChannelSummaryUpdatedEvent;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.casehub.qhorus.api.store.ChannelSummaryStore;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantChannelSummaryStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ChannelSummaryScheduler {

    private static final Logger LOG = Logger.getLogger(ChannelSummaryScheduler.class);

    private final QhorusConfig config;
    private final CrossTenantChannelSummaryStore crossTenantSummaryStore;
    private final ChannelSummaryStore summaryStore;
    private final CrossTenantChannelStore crossTenantChannelStore;
    private final CrossTenantMessageStore crossTenantMessageStore;
    private final SummaryUpdateHook hook;
    private final Consumer<ChannelSummaryUpdatedEvent> summaryConsumer;

    public ChannelSummaryScheduler(QhorusConfig config,
                                    CrossTenantChannelSummaryStore crossTenantSummaryStore,
                                    ChannelSummaryStore summaryStore,
                                    CrossTenantChannelStore crossTenantChannelStore,
                                    CrossTenantMessageStore crossTenantMessageStore,
                                    SummaryUpdateHook hook,
                                    Consumer<ChannelSummaryUpdatedEvent> summaryConsumer) {
        this.config = config;
        this.crossTenantSummaryStore = crossTenantSummaryStore;
        this.summaryStore = summaryStore;
        this.crossTenantChannelStore = crossTenantChannelStore;
        this.crossTenantMessageStore = crossTenantMessageStore;
        this.hook = hook;
        this.summaryConsumer = summaryConsumer;
    }

    public void sweep() {
        if (!config.summary().enabled()) {
            return;
        }

        List<ChannelSummary> candidates = crossTenantSummaryStore.findWithAutoUpdateConfigured();
        Instant now = Instant.now();

        for (ChannelSummary s : candidates) {
            try {
                if (shouldUpdate(s, now)) {
                    updateSummary(s);
                }
            } catch (Exception e) {
                LOG.warnf("Summary update failed for channel %s: %s", s.channelId(), e.getMessage());
            }
        }
    }

    boolean shouldUpdate(ChannelSummary s, Instant now) {
        if (s.updateAfterMessages() != null) {
            long newMessages = countMessagesSince(s.channelId(), s.lastUpdatedMessageId());
            if (newMessages >= s.updateAfterMessages()) {
                return true;
            }
        }
        if (s.updateAfterSeconds() != null) {
            if (s.updatedAt() == null) {
                return true;
            }
            long elapsed = now.getEpochSecond() - s.updatedAt().getEpochSecond();
            if (elapsed >= s.updateAfterSeconds()) {
                return true;
            }
        }
        return false;
    }

    void updateSummary(ChannelSummary s) {
        Channel ch = crossTenantChannelStore.listAll().stream()
                                            .filter(c -> c.id().equals(s.channelId()))
                                            .findFirst()
                                            .orElse(null);
        if (ch == null) {
            LOG.warnf("Channel not found for summary update: %s", s.channelId());
            return;
        }

        long          messagesSince = countMessagesSince(s.channelId(), s.lastUpdatedMessageId());
        List<Message> recent        = fetchMessagesSince(s.channelId(), s.lastUpdatedMessageId());

        SummaryResult previousResult = s.content() != null
                ? new SummaryResult(s.content(), s.annotations())
                : null;

        SummaryResult updated = hook.update(new SummaryUpdateContext(
                s.channelId(), ch.name(), ch.tenancyId(),
                previousResult, s.lastUpdatedMessageId(), messagesSince,
                recent,
                q -> crossTenantMessageStore.scan(
                        q.toBuilder().channelId(s.channelId()).build())));

        Long maxMessageId = currentMaxMessageId(s.channelId());

        summaryStore.save(s.toBuilder()
                           .content(updated.text())
                           .annotations(updated.annotations())
                           .updatedAt(Instant.now())
                           .updatedBy("system:summary-scheduler")
                           .lastUpdatedMessageId(maxMessageId)
                           .build());

        summaryConsumer.accept(new ChannelSummaryUpdatedEvent(s.channelId(), ch.name(), "system:summary-scheduler"));
    }

    private long countMessagesSince(UUID channelId, Long afterId) {
        if (afterId == null) {
            return crossTenantMessageStore.countByChannel(channelId);
        }
        return crossTenantMessageStore.count(
                MessageQuery.builder().channelId(channelId).afterId(afterId).build());
    }

    private List<Message> fetchMessagesSince(UUID channelId, Long afterId) {
        MessageQuery.Builder qb = MessageQuery.builder().channelId(channelId);
        if (afterId != null) {
            qb.afterId(afterId);
        }
        return crossTenantMessageStore.scan(qb.build());
    }

    private Long currentMaxMessageId(UUID channelId) {
        var msgs = crossTenantMessageStore.scan(
                MessageQuery.builder().channelId(channelId).limit(1).descending(true).build());
        return msgs.isEmpty() ? null : msgs.get(0).id();
    }
}
