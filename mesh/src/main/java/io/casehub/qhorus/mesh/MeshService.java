package io.casehub.qhorus.mesh;


import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.ChannelQuery;
import io.casehub.qhorus.api.store.query.InstanceQuery;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.instance.InstanceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class MeshService implements MeshApi {

    @Inject InstanceService instanceService;
    @Inject ChannelService channelService;
    @Inject MessageDispatcher messageDispatcher;
    @Inject MessageStore messageStore;
    @Inject InstanceStore instanceStore;


    @Override
    public MeshRegistration meshRegister(String instanceId, String description,
                                          Map<String, String> metadata) {
        List<String> caps = metadata != null
                ? metadata.values().stream().toList()
                : List.of();
        Instance inst = instanceService.register(instanceId, description, caps, null, false, metadata);
        return new MeshRegistration(inst.id(), inst.instanceId(), inst.description());
    }

    @Override
    public String meshDeregister(String instanceId) {
        instanceService.deregister(instanceId);
        return "Deregistered: " + instanceId;
    }

    @Override
    public MessageResult meshSendMessage(String channel, String sender,
                                          String type, String content) {
        Channel ch = channelService.findByName(channel)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channel));
        MessageType msgType = MessageType.valueOf(type.toUpperCase());
        DispatchResult result = messageDispatcher.dispatch(
                MessageDispatch.builder()
                        .channelId(ch.id())
                        .sender(sender)
                        .type(msgType)
                        .content(content)
                        .actorType(ActorType.AGENT)
                        .build());
        return new MessageResult(result.messageId(), channel, msgType);
    }

    @Override
    public String meshCheckMessages(String channel, Long afterId) {
        Channel ch = channelService.findByName(channel)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channel));
        MessageQuery.Builder qb = MessageQuery.builder().channelId(ch.id());
        if (afterId != null) {
            qb.afterId(afterId);
        }
        qb.limit(50);
        List<Message> messages = messageStore.scan(qb.build());
        if (messages.isEmpty()) {
            return "No messages in " + channel;
        }
        return messages.stream()
                .map(m -> "[" + m.id() + "] [" + m.messageType() + " from " + m.sender() + "] " + m.content())
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String meshCreateChannel(String name, Map<String, String> metadata) {
        Channel ch = channelService.create(
                ChannelCreateRequest.builder(name).metadata(metadata).build());
        return "Created channel: " + ch.name() + " (id=" + ch.id() + ")";
    }

    @Override
    public String meshListChannels(String metadataKey, String metadataValue) {
        List<Channel> channels;
        if (metadataKey != null && !metadataKey.isBlank()
                && metadataValue != null && !metadataValue.isBlank()) {
            channels = channelService.scan(ChannelQuery.byMetadata(metadataKey, metadataValue));
        } else {
            channels = channelService.scan(ChannelQuery.all());
        }
        if (channels.isEmpty()) {
            return "No channels found";
        }
        return channels.stream()
                .map(ch -> ch.name() + (ch.metadata() != null ? " " + ch.metadata() : ""))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public List<PeerInfo> meshDiscoverPeers(String metadataKey, String metadataValue) {
        List<Instance> peers = instanceStore.scan(InstanceQuery.byMetadata(metadataKey, metadataValue));
        return peers.stream()
                .map(i -> new PeerInfo(i.instanceId(), i.description(), i.metadata()))
                .toList();
    }
}
