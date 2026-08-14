package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CommitmentStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;

@ApplicationScoped
public class NotificationBridgeObserver implements MessageObserver {

    private static final Logger LOG                = Logger.getLogger(NotificationBridgeObserver.class);
    private static final int    MAX_CONTENT_LENGTH = 200;

    private final CommitmentStore    commitmentStore;
    private final DataSourceRegistry dataSourceRegistry;

    @Inject
    public NotificationBridgeObserver(CommitmentStore commitmentStore,
                                      DataSourceRegistry dataSourceRegistry) {
        this.commitmentStore    = commitmentStore;
        this.dataSourceRegistry = dataSourceRegistry;
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
            LOG.debugf("No commitment for correlationId=%s — skipping COMMAND notification", event.correlationId());
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
            LOG.debugf("No commitment for correlationId=%s — skipping PROPOSE notification", event.correlationId());
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

    private void fire(QhorusObligationEvent event) {
        try {
            Optional<DataSource<?>> ds = dataSourceRegistry.resolveSource(
                    NOTIFICATION_DATASOURCE_PATH, PLATFORM_TENANT_ID);
            if (ds.isEmpty()) {
                LOG.warnf("Notification DataSource not available — dropping %s event", event.kind());
                return;
            }
            @SuppressWarnings("unchecked")
            DataSource<Object> source = (DataSource<Object>) ds.get();
            source.add(event);
        } catch (Exception e) {
            LOG.warnf("Failed to fire obligation event %s: %s", event.kind(), e.getMessage());
        }
    }

    static String truncate(String s, int max) {
        if (s == null) {return null;}
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
