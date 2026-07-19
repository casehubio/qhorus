package io.casehub.qhorus.runtime.ledger;

import io.casehub.qhorus.api.spi.PeerReviewRequestedEvent;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.instance.InstanceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ReviewerResolver {

    @Inject
    ChannelStore channelStore;

    @Inject
    InstanceService instanceService;

    @Inject
    Event<PeerReviewRequestedEvent> reviewRequestedEvent;

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

        reviewRequestedEvent.fireAsync(
                new PeerReviewRequestedEvent(ledgerEntryId, channelId, tenancyId));
        return List.of();
    }
}
