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
import java.util.Optional;

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
    public DeregisterResult meshDeregister(String instanceId) {
        instanceService.deregister(instanceId);
        return new DeregisterResult(instanceId);
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
    public MeshMessagePage meshCheckMessages(String channel, Optional<Long> afterId) {
        Channel ch = channelService.findByName(channel)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channel));
        MessageQuery.Builder qb = MessageQuery.builder().channelId(ch.id());
        afterId.ifPresent(qb::afterId);
        qb.limit(50);
        List<Message> messages = messageStore.scan(qb.build());
        List<MeshMessage> items = messages.stream()
                .map(m -> new MeshMessage(m.id(), m.messageType().name(), m.sender(), m.content()))
                .toList();
        return new MeshMessagePage(channel, items);
    }

    @Override
    public MeshChannel meshCreateChannel(String name, Map<String, String> metadata) {
        Channel ch = channelService.create(
                ChannelCreateRequest.builder(name).metadata(metadata).build());
        return new MeshChannel(ch.id(), ch.name(), ch.metadata());
    }

    @Override
    public List<MeshChannel> meshListChannels(Optional<String> metadataKey, Optional<String> metadataValue) {
        List<Channel> channels;
        if (metadataKey.filter(k -> !k.isBlank()).isPresent()
                && metadataValue.filter(v -> !v.isBlank()).isPresent()) {
            channels = channelService.scan(ChannelQuery.byMetadata(metadataKey.get(), metadataValue.get()));
        } else {
            channels = channelService.scan(ChannelQuery.all());
        }
        return channels.stream()
                .map(ch -> new MeshChannel(ch.id(), ch.name(), ch.metadata()))
                .toList();
    }

    @Override
    public List<PeerInfo> meshDiscoverPeers(String metadataKey, String metadataValue) {
        List<Instance> peers = instanceStore.scan(InstanceQuery.byMetadata(metadataKey, metadataValue));
        return peers.stream()
                .map(i -> new PeerInfo(i.instanceId(), i.description(), i.metadata()))
                .toList();
    }
}
