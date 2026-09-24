package io.casehub.qhorus.notification.bridge;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.ChannelMembership;
import io.casehub.qhorus.api.gateway.AgentChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;

@ApplicationScoped
public class NotificationChannelBackend implements AgentChannelBackend {

    private static final Logger LOG = Logger.getLogger(NotificationChannelBackend.class);
    private static final String BROADCAST_PREFIX = "broadcast/";

    private final ChannelMembershipStore membershipStore;
    private final DataSourceRegistry dataSourceRegistry;

    @Inject
    public NotificationChannelBackend(ChannelMembershipStore membershipStore,
                                       DataSourceRegistry dataSourceRegistry) {
        this.membershipStore = membershipStore;
        this.dataSourceRegistry = dataSourceRegistry;
    }

    @Override
    public String backendId() { return "platform-notifications"; }

    @Override
    public ActorType actorType() { return ActorType.SYSTEM; }

    @Override
    public DeliveryGuarantee deliveryGuarantee() { return DeliveryGuarantee.BEST_EFFORT; }

    @Override
    public void open(ChannelRef channel, Map<String, String> metadata) {}

    @Override
    public void close(ChannelRef channel) {}

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        String channelName = channel.name();
        if (!channelName.startsWith(BROADCAST_PREFIX)) {
            return;
        }
        String capabilityTag = channelName.substring(BROADCAST_PREFIX.length());

        List<ChannelMembership> members = membershipStore.findByChannel(channel.id());

        Optional<DataSource<?>> ds = dataSourceRegistry.resolveSource(
                NOTIFICATION_DATASOURCE_PATH, PLATFORM_TENANT_ID);
        if (ds.isEmpty()) {
            LOG.warnf("Notification DataSource not available — dropping broadcast to %s", channelName);
            return;
        }

        @SuppressWarnings("unchecked")
        DataSource<Object> source = (DataSource<Object>) ds.get();

        for (ChannelMembership member : members) {
            if (member.memberId().equals(message.sender())) {
                continue;
            }
            try {
                source.add(new QhorusBroadcastEvent(
                        PLATFORM_TENANT_ID,
                        member.memberId(),
                        capabilityTag,
                        channel.id(),
                        channelName,
                        message.sender(),
                        message.type().name(),
                        message.content()));
            } catch (Exception e) {
                LOG.warnf("Failed to deliver broadcast to %s: %s", member.memberId(), e.getMessage());
            }
        }
    }
}
