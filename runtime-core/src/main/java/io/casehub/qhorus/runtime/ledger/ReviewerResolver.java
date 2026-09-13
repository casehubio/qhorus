package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.api.spi.PeerReviewRequestedEvent;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.instance.InstanceService;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ReviewerResolver {

    ChannelStore channelStore;
    InstanceService instanceService;
    Consumer<PeerReviewRequestedEvent> reviewRequestedConsumer;


    ReviewerResolver() {}

    public ReviewerResolver(ChannelStore channelStore,
                            InstanceService instanceService,
                            Consumer<PeerReviewRequestedEvent> reviewRequestedConsumer) {
        this.channelStore = channelStore;
        this.instanceService = instanceService;
        this.reviewRequestedConsumer = reviewRequestedConsumer;
    }

    public List<String> resolve(UUID channelId, List<String> explicitReviewerIds,
                         UUID ledgerEntryId, String tenancyId) {
        if (explicitReviewerIds != null && !explicitReviewerIds.isEmpty()) {
            return explicitReviewerIds;
        }

        var ch = channelStore.find(channelId);
        if (ch.isPresent() && !ch.get().reviewerInstances().isEmpty()) {
            return ch.get().reviewerInstances();
        }

        var capable = instanceService.findByCapability("peer-reviewer");
        if (!capable.isEmpty()) {
            return capable.stream()
                    .map(inst -> inst.instanceId())
                    .toList();
        }

        reviewRequestedConsumer.accept(
                new PeerReviewRequestedEvent(ledgerEntryId, channelId, tenancyId));
        return List.of();
    }
}
