package io.casehub.qhorus.runtime.channel;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSummary;
import io.casehub.qhorus.api.channel.ChannelSummaryUpdatedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.spi.SummaryResult;
import io.casehub.qhorus.api.spi.SummaryUpdateContext;
import io.casehub.qhorus.api.spi.SummaryUpdateHook;
import io.casehub.qhorus.api.store.ChannelSummaryStore;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class ChannelSummaryService implements io.casehub.qhorus.api.channel.ChannelSummaryManager {

    ChannelSummaryStore                  summaryStore;
    ChannelService                       channelService;
    CrossTenantChannelStore              crossTenantChannelStore;
    MessageStore                         messageStore;
    SummaryUpdateHook                    hook;
    Consumer<ChannelSummaryUpdatedEvent> summaryConsumer;


    ChannelSummaryService() {}

    public ChannelSummaryService(ChannelSummaryStore summaryStore,
                                 ChannelService channelService,
                                 CrossTenantChannelStore crossTenantChannelStore,
                                 MessageStore messageStore,
                                 SummaryUpdateHook hook,
                                 Consumer<ChannelSummaryUpdatedEvent> summaryConsumer) {
        this.summaryStore            = summaryStore;
        this.channelService          = channelService;
        this.crossTenantChannelStore = crossTenantChannelStore;
        this.messageStore            = messageStore;
        this.hook                    = hook;
        this.summaryConsumer         = summaryConsumer;
    }

    @Override
    public Optional<ChannelSummary> getSummary(UUID channelId) {
        return summaryStore.findByChannelId(channelId);
    }

    @Override
    public ChannelSummary setSummary(UUID channelId, String content, String updatedBy) {
        Channel ch = channelService.findById(channelId)
                                   .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        Long maxMessageId = currentMaxMessageId(channelId);

        ChannelSummary existing = summaryStore.findByChannelId(channelId).orElse(null);
        ChannelSummary.Builder b = existing != null
                                   ? existing.toBuilder()
                                   : ChannelSummary.builder(channelId).tenancyId(ch.tenancyId());

        ChannelSummary saved = summaryStore.save(b
                                                         .content(content)
                                                         .updatedAt(Instant.now())
                                                         .updatedBy(updatedBy)
                                                         .lastUpdatedMessageId(maxMessageId)
                                                         .build());

        summaryConsumer.accept(new ChannelSummaryUpdatedEvent(channelId, ch.name(), updatedBy));
        return saved;
    }

    @Override
    public ChannelSummary configureSummary(UUID channelId, Integer updateAfterMessages, Integer updateAfterSeconds) {
        if (updateAfterMessages != null && updateAfterMessages < 1) {
            throw new IllegalArgumentException("updateAfterMessages must be >= 1 or null");
        }
        if (updateAfterSeconds != null && updateAfterSeconds < 1) {
            throw new IllegalArgumentException("updateAfterSeconds must be >= 1 or null");
        }

        Channel ch = channelService.findById(channelId)
                                   .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        ChannelSummary existing = summaryStore.findByChannelId(channelId).orElse(null);
        ChannelSummary.Builder b = existing != null
                                   ? existing.toBuilder()
                                   : ChannelSummary.builder(channelId).tenancyId(ch.tenancyId());

        return summaryStore.save(b
                                         .updateAfterMessages(updateAfterMessages)
                                         .updateAfterSeconds(updateAfterSeconds)
                                         .build());
    }

    @Override
    public Optional<ChannelSummary> triggerUpdate(UUID channelId) {
        ChannelSummary existing = summaryStore.findByChannelId(channelId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }

        Channel ch = crossTenantChannelStore.findById(channelId)
                                            .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));

        long          messagesSince = countMessagesSince(channelId, existing.lastUpdatedMessageId());
        List<Message> recent        = fetchMessagesSince(channelId, existing.lastUpdatedMessageId());

        SummaryResult previousResult = existing.content() != null
                                       ? new SummaryResult(existing.content(), existing.annotations())
                                       : null;

        SummaryResult updated = hook.update(new SummaryUpdateContext(
                channelId, ch.name(), ch.tenancyId(), previousResult,
                existing.lastUpdatedMessageId(), messagesSince,
                recent,
                q -> messageStore.scan(q.toBuilder().channelId(channelId).build())));

        Long maxMessageId = currentMaxMessageId(channelId);

        ChannelSummary saved = summaryStore.save(existing.toBuilder()
                                                         .content(updated.text())
                                                         .annotations(updated.annotations())
                                                         .updatedAt(Instant.now())
                                                         .updatedBy("system:summary-scheduler")
                                                         .lastUpdatedMessageId(maxMessageId)
                                                         .build());

        summaryConsumer.accept(new ChannelSummaryUpdatedEvent(channelId, ch.name(), "system:summary-scheduler"));
        return Optional.of(saved);
    }

    public long countMessagesSince(UUID channelId, Long afterId) {
        long cursor = afterId != null ? afterId : 0L;
        return messageStore.count(MessageQuery.builder().channelId(channelId).afterId(cursor).build());
    }

    List<Message> fetchMessagesSince(UUID channelId, Long afterId) {
        long cursor = afterId != null ? afterId : 0L;
        return messageStore.scan(MessageQuery.builder().channelId(channelId).afterId(cursor).build());
    }

    Long currentMaxMessageId(UUID channelId) {
        return messageStore.findLastMessage(channelId).map(Message::id).orElse(null);
    }
}
