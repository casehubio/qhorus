package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.subscription.SubscribableEvent;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.CommitmentStore;
import io.casehub.qhorus.notification.bridge.core.CommitmentEventNotifierCore;
import io.casehub.qhorus.notification.bridge.core.NotificationBridgeObserverCore;
import io.casehub.qhorus.notification.bridge.core.NotificationChannelBackendCore;
import io.casehub.qhorus.notification.bridge.core.QhorusSubscriptionBootstrapCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.function.Consumer;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;

@ApplicationScoped
public class NotificationBridgeBeans {

    private static final Logger LOG = Logger.getLogger(NotificationBridgeBeans.class);

    @Produces
    @ApplicationScoped
    public NotificationBridgeObserverCore notificationBridgeObserver(
            CommitmentStore commitmentStore,
            DataSourceRegistry dataSourceRegistry) {
        return new NotificationBridgeObserverCore(commitmentStore, eventSink(dataSourceRegistry));
    }

    @Produces
    @ApplicationScoped
    public CommitmentEventNotifierCore commitmentEventNotifier(
            CommitmentStore commitmentStore,
            DataSourceRegistry dataSourceRegistry) {
        return new CommitmentEventNotifierCore(commitmentStore, eventSink(dataSourceRegistry));
    }

    @Produces
    @ApplicationScoped
    public NotificationChannelBackendCore notificationChannelBackend(
            ChannelMembershipStore membershipStore,
            DataSourceRegistry dataSourceRegistry) {
        return new NotificationChannelBackendCore(membershipStore, eventSink(dataSourceRegistry));
    }

    @Produces
    @ApplicationScoped
    public QhorusSubscriptionBootstrapCore subscriptionBootstrap(SubscriptionStore subscriptionStore) {
        return new QhorusSubscriptionBootstrapCore(subscriptionStore);
    }

    private Consumer<SubscribableEvent> eventSink(DataSourceRegistry registry) {
        return event -> {
            try {
                Optional<DataSource<?>> ds = registry.resolveSource(
                        NOTIFICATION_DATASOURCE_PATH, PLATFORM_TENANT_ID);
                if (ds.isEmpty()) {
                    LOG.warnf("Notification DataSource not available — dropping event");
                    return;
                }
                @SuppressWarnings("unchecked")
                DataSource<Object> source = (DataSource<Object>) ds.get();
                source.add(event);
            } catch (Exception e) {
                LOG.warnf("Failed to fire notification event: %s", e.getMessage());
            }
        };
    }
}
