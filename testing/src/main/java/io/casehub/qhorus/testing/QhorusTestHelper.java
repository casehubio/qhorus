package io.casehub.qhorus.testing;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.data.SharedData;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.DataStore;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.api.watchdog.Watchdog;
import io.casehub.qhorus.api.watchdog.WatchdogAction;
import io.casehub.qhorus.api.watchdog.WatchdogConditionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class QhorusTestHelper {

    @Inject ChannelManager channelManager;
    @Inject ChannelStore channelStore;
    @Inject MessageDispatcher messageDispatcher;
    @Inject MessageStore messageStore;
    @Inject InstanceStore instanceStore;
    @Inject DataStore dataStore;
    @Inject WatchdogStore watchdogStore;

    // --- Channel creation ---

    public Channel createChannel(String name) {
        return channelManager.create(ChannelCreateRequest.builder(name).build());
    }

    public Channel createChannel(String name, ChannelSemantic semantic) {
        return channelManager.create(ChannelCreateRequest.builder(name)
                .semantic(semantic)
                .build());
    }

    public Channel createChannel(String name, ChannelSemantic semantic,
                                 List<String> barrierContributors) {
        return channelManager.create(ChannelCreateRequest.builder(name)
                .semantic(semantic)
                .barrierContributors(barrierContributors)
                .build());
    }

    public Channel createChannel(ChannelCreateRequest request) {
        return channelManager.create(request);
    }

    // --- Message sending ---

    public DispatchResult sendMessage(String channelName, String sender,
                                      String type, String content) {
        return sendMessage(channelName, sender, type, content, null, null, null,
                null, null, null, null, null, null);
    }

    public DispatchResult sendMessage(String channelName, String sender,
                                      String type, String content,
                                      String correlationId) {
        return sendMessage(channelName, sender, type, content, null,
                correlationId, null, null, null, null, null, null, null);
    }

    public DispatchResult sendMessage(String channelName, String sender,
                                      String type, String content,
                                      String correlationId, Long inReplyTo) {
        return sendMessage(channelName, sender, type, content, null,
                correlationId, inReplyTo, null, null, null, null, null, null);
    }

    public DispatchResult sendMessage(String channelName, String sender,
                                      String type, String content,
                                      String payload, String correlationId,
                                      Long inReplyTo, String artefactRefs,
                                      String target, String deadline,
                                      String subjectId, String causedByEntryId,
                                      String topic) {
        UUID channelId = resolveChannelId(channelName);
        var builder = MessageDispatch.builder()
                .channelId(channelId)
                .sender(sender)
                .type(MessageType.valueOf(type.toUpperCase()))
                .actorType(ActorType.AGENT);

        if (content != null) builder.content(content);
        if (payload != null) builder.payload(payload);
        if (correlationId != null) builder.correlationId(correlationId);
        if (inReplyTo != null) builder.inReplyTo(inReplyTo);
        if (target != null) builder.target(target);
        if (topic != null) builder.topic(topic);
        if (subjectId != null) builder.subjectId(UUID.fromString(subjectId));
        if (causedByEntryId != null) builder.causedByEntryId(UUID.fromString(causedByEntryId));

        return messageDispatcher.dispatch(builder.build());
    }

    public DispatchResult dispatch(MessageDispatch dispatch) {
        return messageDispatcher.dispatch(dispatch);
    }

    // --- Message queries ---

    public List<Message> checkMessages(String channelName, Long afterId, int limit) {
        return checkMessages(channelName, afterId, limit, null, null, null);
    }

    public List<Message> checkMessages(String channelName, Long afterId, int limit,
                                       String sender, String readerInstanceId,
                                       Boolean includeEvents) {
        UUID channelId = resolveChannelId(channelName);
        var queryBuilder = MessageQuery.builder()
                .channelId(channelId)
                .limit(limit);
        if (afterId != null && afterId > 0) queryBuilder.afterId(afterId);
        if (sender != null) queryBuilder.sender(sender);
        if (includeEvents == null || !includeEvents) {
            queryBuilder.excludeTypes(List.of(MessageType.EVENT));
        }
        return messageStore.scan(queryBuilder.build());
    }

    // --- Instance management ---

    public Instance registerInstance(String instanceId, String description,
                                     String... capabilities) {
        var instance = Instance.builder(instanceId)
                .description(description)
                .status("online")
                .build();
        var saved = instanceStore.put(instance);
        if (capabilities != null && capabilities.length > 0) {
            instanceStore.putCapabilities(saved.id(), List.of(capabilities));
        }
        return saved;
    }

    // --- Artefact management ---

    public SharedData shareArtefact(String key, String description,
                                    String content, String createdBy) {
        var data = SharedData.builder(key)
                .description(description)
                .content(content)
                .createdBy(createdBy)
                .complete(true)
                .sizeBytes(content != null ? content.length() : 0)
                .build();
        return dataStore.put(data);
    }

    // --- Watchdog ---

    public Watchdog registerWatchdog(String conditionType, String targetName,
                                     Integer thresholdSeconds, Integer thresholdCount,
                                     Integer similarityPct, String notificationChannel,
                                     String createdBy, String action) {
        var watchdog = Watchdog.builder(
                        WatchdogConditionType.valueOf(conditionType), targetName)
                .thresholdSeconds(thresholdSeconds)
                .thresholdCount(thresholdCount)
                .similarityPct(similarityPct)
                .notificationChannel(notificationChannel)
                .createdBy(createdBy)
                .action(action != null ? WatchdogAction.valueOf(action) : WatchdogAction.ALERT)
                .build();
        return watchdogStore.put(watchdog);
    }

    // --- Channel operations ---

    public void deleteChannel(String channelName, boolean force) {
        UUID channelId = resolveChannelId(channelName);
        channelManager.delete(channelId, force);
    }

    public void pauseChannel(String channelName) {
        channelManager.pause(resolveChannelId(channelName));
    }

    public void resumeChannel(String channelName) {
        channelManager.resume(resolveChannelId(channelName));
    }

    // --- Utilities ---

    public UUID resolveChannelId(String channelName) {
        return channelStore.findByName(channelName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Channel not found: " + channelName))
                .id();
    }

    public Channel findChannel(String channelName) {
        return channelStore.findByName(channelName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Channel not found: " + channelName));
    }
}
