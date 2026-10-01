package io.casehub.qhorus.slack.core;

import static io.casehub.qhorus.api.message.MessageType.DECLINE;
import static io.casehub.qhorus.api.message.MessageType.DONE;
import static io.casehub.qhorus.api.message.MessageType.EVENT;
import static io.casehub.qhorus.api.message.MessageType.FAILURE;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.casehub.connectors.InboundConnectorIds;
import io.casehub.connectors.InboundMessage;
import io.casehub.connectors.slack.bot.SlackBotClient;
import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.ChannelInitialisedEvent;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.DeliveryGuarantee;
import io.casehub.qhorus.api.gateway.HumanParticipatingChannelBackend;
import io.casehub.qhorus.api.gateway.InboundHumanMessage;
import io.casehub.qhorus.api.gateway.InboundNormaliser;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;

public class SlackChannelBackendCore implements HumanParticipatingChannelBackend {

    public static final String BACKEND_ID = "slack-bot";

    private static final Logger LOG = Logger.getLogger(SlackChannelBackendCore.class.getName());

    private final SlackBotBindingStore bindingStore;
    private final SlackThreadCacheStore threadCacheStore;
    private final SlackBotClient slackBotClient;
    private final SlackInboundNormaliser slackInboundNormaliser;
    private final ChannelGateway gateway;
    private final CredentialResolver credentialResolver;

    final ConcurrentHashMap<UUID, SlackBotBinding> bindingCache = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, ChannelRef> slackToChannel = new ConcurrentHashMap<>();
    final ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>> threadCache = new ConcurrentHashMap<>();

    public SlackChannelBackendCore(SlackBotBindingStore bindingStore,
                                   SlackThreadCacheStore threadCacheStore,
                                   SlackBotClient slackBotClient,
                                   SlackInboundNormaliser slackInboundNormaliser,
                                   ChannelGateway gateway,
                                   CredentialResolver credentialResolver) {
        this.bindingStore = bindingStore;
        this.threadCacheStore = threadCacheStore;
        this.slackBotClient = slackBotClient;
        this.slackInboundNormaliser = slackInboundNormaliser;
        this.gateway = gateway;
        this.credentialResolver = credentialResolver;
    }

    @Override
    public String backendId() {
        return BACKEND_ID;
    }

    @Override
    public InboundNormaliser normaliserFor(UUID channelId) {
        return slackInboundNormaliser;
    }

    @Override
    public ActorType actorType() {
        return ActorType.HUMAN;
    }

    @Override
    public DeliveryGuarantee deliveryGuarantee() {
        return DeliveryGuarantee.AT_LEAST_ONCE;
    }

    @Override
    public void open(ChannelRef channel, Map<String, String> metadata) {
    }

    public void onChannelInitialised(ChannelInitialisedEvent event) {
        UUID channelId = event.channelId();
        bindingStore.findByChannelId(channelId).ifPresent(binding -> {
            bindingCache.put(channelId, binding);
            slackToChannel.put(binding.slackChannelId, new ChannelRef(channelId, event.channelName()));

            List<SlackThreadCache> entries = threadCacheStore.findByChannelId(channelId);
            if (!entries.isEmpty()) {
                ConcurrentHashMap<String, String> channelThreads = threadCache
                        .computeIfAbsent(channelId, k -> new ConcurrentHashMap<>());
                entries.forEach(e -> channelThreads.put(e.id.correlationId, e.threadTs));
            }

            gateway.deregisterBackend(channelId, BACKEND_ID);
            gateway.registerBackend(channelId, this, "human_participating");
        });
    }

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        if (message.type() == EVENT) return;
        if (message.content() == null) return;

        SlackBotBinding binding = bindingCache.get(channel.id());
        if (binding == null) {
            LOG.fine(() -> "No Slack binding for channel " + channel.name() + " — skipping post");
            return;
        }

        String token;
        try {
            token = resolveToken(binding.workspaceId);
        } catch (NoSuchElementException e) {
            LOG.warning("No credential configured for workspace " + binding.workspaceId
                    + " on channel " + channel.name() + " — skipping post");
            return;
        }

        String threadTs = null;
        if (message.correlationId() != null) {
            String corrId = message.correlationId();
            Map<String, String> channelThreads = threadCache.get(channel.id());
            threadTs = channelThreads != null ? channelThreads.get(corrId) : null;
            if (threadTs == null) {
                threadTs = threadCacheStore.findThreadTs(channel.id(), corrId).orElse(null);
            }
        }

        SlackBotClient.PostResult result = slackBotClient.postMessage(
                token, binding.slackChannelId, message.content(), threadTs);

        if (!result.ok()) {
            LOG.warning("Slack post failed on channel " + channel.name() + ": " + result.error());
            return;
        }

        boolean isTerminal = message.type() == DONE || message.type() == FAILURE || message.type() == DECLINE;

        if (message.correlationId() != null && threadTs == null && result.ts() != null && !isTerminal) {
            String corrId = message.correlationId();
            threadCache.computeIfAbsent(channel.id(), k -> new ConcurrentHashMap<>())
                    .put(corrId, result.ts());
            threadCacheStore.save(channel.id(), corrId, result.ts());
        }

        if (isTerminal && message.correlationId() != null) {
            String corrId = message.correlationId();
            Map<String, String> channelThreads = threadCache.get(channel.id());
            if (channelThreads != null) channelThreads.remove(corrId);
            threadCacheStore.delete(channel.id(), corrId);
        }
    }

    public CompletionStage<Void> onInboundMessage(InboundMessage msg) {
        if (!InboundConnectorIds.SLACK_INBOUND.equals(msg.connectorId())) {
            return CompletableFuture.completedFuture(null);
        }

        ChannelRef channelRef = slackToChannel.get(msg.externalChannelRef());
        if (channelRef == null) {
            LOG.fine(() -> "No Slack-backed channel for Slack channel " + msg.externalChannelRef() + " — discarding");
            return CompletableFuture.completedFuture(null);
        }

        String slackThreadTs = msg.metadata().get("slack-thread-ts");
        String slackTs = msg.metadata().get("slack-ts");
        String corrIdStr;

        if (slackThreadTs != null && !slackThreadTs.equals(slackTs)) {
            corrIdStr = threadCacheStore.findCorrelationId(channelRef.id(), slackThreadTs)
                    .orElse(null);
        } else {
            corrIdStr = null;
        }

        if (corrIdStr == null) {
            String corrId = UUID.randomUUID().toString();
            corrIdStr = corrId;
            String rootTs = (slackThreadTs != null && !slackThreadTs.equals(slackTs)) ? slackThreadTs : slackTs;
            if (rootTs != null) {
                threadCache.computeIfAbsent(channelRef.id(), k -> new ConcurrentHashMap<>())
                        .put(corrId, rootTs);
                try {
                    threadCacheStore.save(channelRef.id(), corrId, rootTs);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Thread anchor DB write failed for channel=" + channelRef.id()
                            + " corrId=" + corrId + " — in-memory anchor intact", e);
                }
            }
        }

        gateway.receiveHumanMessage(channelRef,
                new InboundHumanMessage(msg.externalSenderId(), msg.content(), msg.receivedAt(),
                        msg.metadata(), corrIdStr, null));
        return CompletableFuture.completedFuture(null);
    }

    public void evict(UUID channelId) {
        SlackBotBinding binding = bindingCache.remove(channelId);
        if (binding != null) slackToChannel.remove(binding.slackChannelId);
        threadCache.remove(channelId);
    }

    @Override
    public void close(ChannelRef channel) {
        SlackBotBinding binding = bindingCache.remove(channel.id());
        if (binding != null) slackToChannel.remove(binding.slackChannelId);
        threadCache.remove(channel.id());
        threadCacheStore.deleteAllByChannelId(channel.id());
        bindingStore.deleteByChannelId(channel.id());
    }

    public void evictStaleThreadCacheEntries(Instant threshold) {
        int deleted = threadCacheStore.deleteOlderThan(threshold);
        if (deleted > 0) {
            for (var channelEntry : threadCache.entrySet()) {
                UUID channelId = channelEntry.getKey();
                Map<String, String> channelThreads = channelEntry.getValue();
                channelThreads.keySet().removeIf(corrId ->
                        threadCacheStore.findThreadTs(channelId, corrId).isEmpty());
            }
        }
    }

    String resolveToken(String workspaceId) {
        Map<String, String> creds = credentialResolver.resolve(workspaceId);
        String token = creds.get(CredentialPropertyKeys.BEARER_TOKEN);
        if (token == null || token.isBlank()) {
            throw new NoSuchElementException("No bearer-token for credential ref: " + workspaceId);
        }
        return token;
    }
}
