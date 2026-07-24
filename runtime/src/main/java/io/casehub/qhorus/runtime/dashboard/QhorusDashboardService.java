package io.casehub.qhorus.runtime.dashboard;

import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@ApplicationScoped
public class QhorusDashboardService {

    @Inject
    ChannelService                               channelService;
    @Inject
    InstanceService                              instanceService;
    @Inject
    MessageService                               messageService;
    @Inject
    MessageStore                                 messageStore;
    @Inject
    io.casehub.qhorus.runtime.QhorusEntityMapper entityMapper;
    @Inject
    ChannelBindingStore                          bindingStore;

    public record HumanMessageResult(
            Long messageId, String channelName, String sender, String messageType,
            String correlationId, Long inReplyTo, int parentReplyCount,
            java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, String target) {}

    public List<ChannelDetail> listChannels() {
        final var bindings = bindingStore.findAll();
        final var channels = channelService.listAll();
        if (channels.isEmpty()) {return List.of();}
        return channels.stream()
                       .map(ch -> entityMapper.toChannelDetail(ch, messageStore.countByChannel(ch.id()),
                                                               Optional.ofNullable(bindings.get(ch.id()))))
                       .toList();
    }

    public List<InstanceInfo> listInstances() {
        final var instances = instanceService.listAll();
        if (instances.isEmpty()) {return List.of();}
        return instances.stream()
                        .map(i -> new InstanceInfo(
                                i.instanceId(), i.description(), i.status(),
                                instanceService.findCapabilityTagsForInstance(i.instanceId()),
                                i.lastSeen().toString(), i.readOnly()))
                        .toList();
    }

    public List<Map<String, Object>> getTimeline(String channelName, Long afterId, int limit) {
        final var opt = channelService.findByName(channelName);
        if (opt.isEmpty()) {return List.of();}
        int effectiveLimit = Math.min(Math.max(limit, 1), 200);
        return messageStore.scan(MessageQuery.poll(opt.get().id(), afterId, effectiveLimit))
                           .stream().map(entityMapper::toTimelineEntry).toList();
    }

    public List<Map<String, Object>> getFeed(int limit) {
        int       effectiveLimit = Math.min(Math.max(limit, 1), 200);
        final var channels       = channelService.listAll();
        if (channels.isEmpty()) {return List.of();}
        Map<UUID, String> nameMap = channels.stream()
                                            .collect(Collectors.toMap(ch -> ch.id(), ch -> ch.name()));
        return messageStore.scan(MessageQuery.recent(effectiveLimit)).stream()
                           .map(m -> {
                               Map<String, Object> entry = new HashMap<>(entityMapper.toTimelineEntry(m));
                               entry.put("channel", nameMap.getOrDefault(m.channelId(), m.channelId().toString()));
                               return entry;
                           })
                           .toList();
    }

    public HumanMessageResult sendHumanMessage(
            String channelName, String sender, MessageType type, String content,
            Long inReplyTo, String correlationId,
            java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs,
            String target, java.time.Instant deadline, String topic) {
        final var ch = channelService.findByName(channelName)
                                     .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelName));
        final var result = messageService.dispatch(
                MessageDispatch.builder()
                               .channelId(ch.id())
                               .sender(sender)
                               .type(type)
                               .content(content)
                               .actorType(io.casehub.platform.api.identity.ActorType.HUMAN)
                               .inReplyTo(inReplyTo)
                               .correlationId(correlationId)
                               .artefactRefs(artefactRefs)
                               .target(target)
                               .deadline(deadline)
                               .topic(topic)
                               .build());
        return new HumanMessageResult(
                result.messageId(), channelName, result.sender(),
                result.type() != null ? result.type().name() : null,
                result.correlationId(), result.inReplyTo(),
                result.parentReplyCount(),
                result.artefactRefs(),
                result.target());
    }
}

