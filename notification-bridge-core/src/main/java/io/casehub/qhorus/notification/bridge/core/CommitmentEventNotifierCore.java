package io.casehub.qhorus.notification.bridge.core;

import io.casehub.platform.api.subscription.SubscribableEvent;
import io.casehub.qhorus.api.message.Commitment;

import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.CommitmentExpiredEvent;
import io.casehub.qhorus.api.store.CommitmentStore;

import java.util.Optional;
import java.util.function.Consumer;

public class CommitmentEventNotifierCore {

    private final CommitmentStore commitmentStore;
    private final Consumer<SubscribableEvent> eventSink;

    public CommitmentEventNotifierCore(CommitmentStore commitmentStore,
                                       Consumer<SubscribableEvent> eventSink) {
        this.commitmentStore = commitmentStore;
        this.eventSink = eventSink;
    }

    public void onDeclined(CommitmentDeclinedEvent event) {
        String requester = event.requester();
        if (requester == null || requester.isBlank()) {
            return;
        }
        Optional<Commitment> commitment = commitmentStore.findById(event.commitmentId());
        String tenancyId = commitment.map(Commitment::tenancyId).orElse("DEFAULT");

        fire(new QhorusObligationEvent(
                QhorusObligationEvent.Kind.DECLINED,
                tenancyId,
                event.obligor(),
                requester,
                event.channelId(),
                null,
                event.obligor(),
                event.correlationId(),
                null));
    }

    public void onExpired(CommitmentExpiredEvent event) {
        String requester = event.requester();
        if (requester == null || requester.isBlank()) {
            return;
        }
        Optional<Commitment> commitment = commitmentStore.findById(event.commitmentId());
        String tenancyId = commitment.map(Commitment::tenancyId).orElse("DEFAULT");

        fire(new QhorusObligationEvent(
                QhorusObligationEvent.Kind.EXPIRED,
                tenancyId,
                event.obligor(),
                requester,
                event.channelId(),
                null,
                event.obligor(),
                event.correlationId(),
                null));
    }

    private void fire(SubscribableEvent event) {
        try {
            eventSink.accept(event);
        } catch (Exception e) {
            // notification failure must not crash the notifier
        }
    }
}
