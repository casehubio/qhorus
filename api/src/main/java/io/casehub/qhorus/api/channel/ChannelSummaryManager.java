package io.casehub.qhorus.api.channel;

import java.util.Optional;
import java.util.UUID;

public interface ChannelSummaryManager {

    Optional<ChannelSummary> getSummary(UUID channelId);

    ChannelSummary setSummary(UUID channelId, String summary, String actorId);

    ChannelSummary configureSummary(UUID channelId, Integer updateAfterMessages, Integer updateAfterSeconds);

    Optional<ChannelSummary> triggerUpdate(UUID channelId);

    void deleteSummary(UUID channelId);

}
