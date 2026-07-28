package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentDeclinedEvent;
import io.casehub.qhorus.api.message.CommitmentExpiredEvent;
import io.casehub.qhorus.api.store.CommitmentStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;

@ApplicationScoped
public class CommitmentEventNotifier {

    private static final Logger LOG = Logger.getLogger(CommitmentEventNotifier.class);

    private final CommitmentStore    commitmentStore;
    private final DataSourceRegistry dataSourceRegistry;

    @Inject
    public CommitmentEventNotifier(CommitmentStore commitmentStore,
                                   DataSourceRegistry dataSourceRegistry) {
        this.commitmentStore    = commitmentStore;
        this.dataSourceRegistry = dataSourceRegistry;
    }

    void onDeclined(@ObservesAsync CommitmentDeclinedEvent event) {
        String requester = event.requester();
        if (requester == null || requester.isBlank()) {
            return;
        }
        Optional<Commitment> commitment = commitmentStore.findById(event.commitmentId());
        String               tenancyId  = commitment.map(Commitment::tenancyId).orElse("DEFAULT");

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

    void onExpired(@ObservesAsync CommitmentExpiredEvent event) {
        String requester = event.requester();
        if (requester == null || requester.isBlank()) {
            return;
        }
        Optional<Commitment> commitment = commitmentStore.findById(event.commitmentId());
        String               tenancyId  = commitment.map(Commitment::tenancyId).orElse("DEFAULT");

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
}
