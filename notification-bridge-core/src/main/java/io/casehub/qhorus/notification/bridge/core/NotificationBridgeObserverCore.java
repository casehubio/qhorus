package io.casehub.qhorus.notification.bridge.core;

import io.casehub.platform.api.subscription.SubscribableEvent;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CommitmentStore;

import java.util.Optional;
import java.util.function.Consumer;

public class NotificationBridgeObserverCore implements MessageObserver {

    private static final int MAX_CONTENT_LENGTH = 200;

    private final CommitmentStore commitmentStore;
    private final Consumer<SubscribableEvent> eventSink;

    public NotificationBridgeObserverCore(CommitmentStore commitmentStore,
                                          Consumer<SubscribableEvent> eventSink) {
        this.commitmentStore = commitmentStore;
        this.eventSink = eventSink;
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (event.correlationId() == null) {
            return;
        }
        switch (event.messageType()) {
            case COMMAND -> fireAssigned(event);
            case PROPOSE -> fireProposed(event);
            case DONE -> fireResolved(event, QhorusObligationEvent.Kind.FULFILLED);
            case FAILURE -> fireResolved(event, QhorusObligationEvent.Kind.FAILED);
            default -> {}
        }
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }

    private void fireAssigned(MessageReceivedEvent event) {
        Optional<Commitment> commitment = commitmentStore.findByCorrelationId(event.correlationId());
        if (commitment.isEmpty()) {
            return;
        }
        String obligor = commitment.get().obligor();
        if (obligor == null || obligor.isBlank()) {
            return;
        }
        fire(new QhorusObligationEvent(
                QhorusObligationEvent.Kind.ASSIGNED,
                event.tenancyId(),
                obligor,
                commitment.get().requester(),
                event.channelId(),
                event.channelName(),
                event.senderId(),
                event.correlationId(),
                truncate(event.content(), MAX_CONTENT_LENGTH)));
    }

    private void fireProposed(MessageReceivedEvent event) {
        Optional<Commitment> commitment = commitmentStore.findByCorrelationId(event.correlationId());
        if (commitment.isEmpty()) {
            return;
        }
        String obligor = commitment.get().obligor();
        if (obligor == null || obligor.isBlank()) {
            return;
        }
        fire(new QhorusObligationEvent(
                QhorusObligationEvent.Kind.PROPOSED,
                event.tenancyId(),
                obligor,
                commitment.get().requester(),
                event.channelId(),
                event.channelName(),
                event.senderId(),
                event.correlationId(),
                truncate(event.content(), MAX_CONTENT_LENGTH)));
    }

    private void fireResolved(MessageReceivedEvent event, QhorusObligationEvent.Kind kind) {
        Optional<Commitment> commitment = commitmentStore.findByCorrelationId(event.correlationId());
        if (commitment.isEmpty()) {
            return;
        }
        String requester = commitment.get().requester();
        if (requester == null || requester.isBlank()) {
            return;
        }
        if (requester.equals(event.senderId())) {
            return;
        }
        fire(new QhorusObligationEvent(
                kind,
                event.tenancyId(),
                commitment.get().obligor(),
                requester,
                event.channelId(),
                event.channelName(),
                event.senderId(),
                event.correlationId(),
                truncate(event.content(), MAX_CONTENT_LENGTH)));
    }

    private void fire(SubscribableEvent event) {
        try {
            eventSink.accept(event);
        } catch (Exception e) {
            // notification failure must not crash the observer
        }
    }

    public static String truncate(String s, int max) {
        if (s == null) { return null; }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
