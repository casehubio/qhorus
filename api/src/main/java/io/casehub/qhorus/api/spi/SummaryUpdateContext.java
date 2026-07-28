package io.casehub.qhorus.api.spi;

import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.store.query.MessageQuery;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public record SummaryUpdateContext(
        UUID channelId,
        String channelName,
        String tenancyId,
        SummaryResult previousResult,
        Long lastUpdatedMessageId,
        long messagesSinceLastUpdate,
        List<Message> recentMessages,
        Function<MessageQuery, List<Message>> messageQuery) {

    public String currentSummary() {
        return previousResult != null ? previousResult.text() : null;
    }
}
