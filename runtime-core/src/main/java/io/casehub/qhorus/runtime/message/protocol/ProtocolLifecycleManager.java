package io.casehub.qhorus.runtime.message.protocol;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.gateway.ChannelClosedEvent;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.ChannelProtocolsChangedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ProtocolLifecycleContext;
import io.casehub.qhorus.api.spi.ProtocolLifecycleHandler;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class ProtocolLifecycleManager {

    private static final Logger LOG = Logger.getLogger(ProtocolLifecycleManager.class);

    private final List<ProtocolLifecycleHandler> handlers;
    private final CrossTenantChannelStore channelStore;
    private final CrossTenantMessageStore messageStore;
    private final Function<Message, MessageView> messageMapper;
    private final ConcurrentHashMap<UUID, List<String>> protocolMap = new ConcurrentHashMap<>();

    public ProtocolLifecycleManager(List<ProtocolLifecycleHandler> handlers,
                                    CrossTenantChannelStore channelStore,
                                    CrossTenantMessageStore messageStore) {
        this(handlers, channelStore, messageStore, ProtocolLifecycleManager::defaultMessageView);
    }

    public ProtocolLifecycleManager(List<ProtocolLifecycleHandler> handlers,
                                    CrossTenantChannelStore channelStore,
                                    CrossTenantMessageStore messageStore,
                                    Function<Message, MessageView> messageMapper) {
        this.handlers = List.copyOf(handlers);
        this.channelStore = channelStore;
        this.messageStore = messageStore;
        this.messageMapper = messageMapper;
    }

    public void onChannelInitialised(ChannelInitialisedEvent event) {
        Channel channel = channelStore.findById(event.channelId()).orElse(null);
        if (channel == null || channel.protocols().isEmpty()) {
            return;
        }

        protocolMap.put(channel.id(), List.copyOf(channel.protocols()));

        List<MessageView> messageHistory = fetchMessageHistory(channel.id());

        for (String protocolName : channel.protocols()) {
            ProtocolLifecycleHandler handler = findHandler(protocolName);
            if (handler == null) {
                continue;
            }
            try {
                handler.onInit(new ProtocolLifecycleContext(
                        channel.id(), channel.name(), protocolName,
                        channel.protocolParticipants(), messageHistory,
                        event.recovered()));
            } catch (Exception e) {
                LOG.warnf(e, "Protocol lifecycle init failed: %s on channel %s",
                        protocolName, channel.name());
            }
        }
    }

    public void onChannelClosed(ChannelClosedEvent event) {
        List<String> protocols = protocolMap.remove(event.channelId());
        if (protocols == null || protocols.isEmpty()) {
            return;
        }

        for (String protocolName : protocols) {
            ProtocolLifecycleHandler handler = findHandler(protocolName);
            if (handler == null) {
                continue;
            }
            try {
                handler.onDestroy(event.channelId(), protocolName);
            } catch (Exception e) {
                LOG.warnf(e, "Protocol lifecycle destroy failed: %s on channel %s",
                        protocolName, event.channelName());
            }
        }
    }

    public void onProtocolsChanged(ChannelProtocolsChangedEvent event) {
        for (String protocolName : event.removedProtocols()) {
            ProtocolLifecycleHandler handler = findHandler(protocolName);
            if (handler != null) {
                try {
                    handler.onDestroy(event.channelId(), protocolName);
                } catch (Exception e) {
                    LOG.warnf(e, "Protocol lifecycle destroy failed: %s on channel %s",
                            protocolName, event.channelName());
                }
            }
        }

        protocolMap.compute(event.channelId(), (id, current) -> {
            List<String> updated = new ArrayList<>(current != null ? current : List.of());
            updated.removeAll(event.removedProtocols());
            updated.addAll(event.addedProtocols());
            return updated.isEmpty() ? null : List.copyOf(updated);
        });

        if (event.addedProtocols().isEmpty()) {
            return;
        }

        Channel channel = channelStore.findById(event.channelId()).orElse(null);
        if (channel == null) {
            return;
        }

        List<MessageView> messageHistory = fetchMessageHistory(event.channelId());

        for (String protocolName : event.addedProtocols()) {
            ProtocolLifecycleHandler handler = findHandler(protocolName);
            if (handler != null) {
                try {
                    handler.onInit(new ProtocolLifecycleContext(
                            event.channelId(), event.channelName(), protocolName,
                            channel.protocolParticipants(), messageHistory, false));
                } catch (Exception e) {
                    LOG.warnf(e, "Protocol lifecycle init failed: %s on channel %s",
                            protocolName, event.channelName());
                }
            }
        }
    }

    private ProtocolLifecycleHandler findHandler(String protocolName) {
        for (ProtocolLifecycleHandler handler : handlers) {
            if (handler.supports(protocolName)) {
                return handler;
            }
        }
        return null;
    }

    private List<MessageView> fetchMessageHistory(UUID channelId) {
        return messageStore.scan(MessageQuery.forChannel(channelId)).stream()
                .map(messageMapper)
                .toList();
    }

    private static MessageView defaultMessageView(Message msg) {
        return new MessageView(
                msg.id(), msg.channelId(), msg.sender(), msg.messageType(),
                msg.content(), msg.payload(), msg.correlationId(), msg.inReplyTo(),
                msg.target(), msg.topic(), msg.artefactRefs(), msg.actorType(),
                msg.createdAt(), msg.deadline(), msg.replyCount(),
                msg.correctsMessageId(), msg.retraction());
    }
}
