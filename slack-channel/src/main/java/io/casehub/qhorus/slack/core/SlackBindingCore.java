package io.casehub.qhorus.slack.core;

import io.casehub.platform.api.credentials.CredentialPropertyKeys;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.gateway.DuplicateParticipatingBackendException;
import io.casehub.qhorus.slack.SlackBindingDto;
import io.casehub.qhorus.slack.SlackBindingRequest;
import io.casehub.qhorus.slack.SlackBotBinding;
import io.casehub.qhorus.slack.SlackBotBindingStore;
import io.casehub.qhorus.slack.SlackChannelBackend;
import io.casehub.qhorus.slack.SlackThreadCacheStore;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class SlackBindingCore {

    private final SlackBotBindingStore bindingStore;
    private final ChannelService channelService;
    private final ChannelGateway gateway;
    private final SlackChannelBackend backend;
    private final ChannelBindingStore channelBindingStore;
    private final SlackThreadCacheStore threadCacheStore;
    private final CredentialResolver credentialResolver;

    public SlackBindingCore(SlackBotBindingStore bindingStore,
                            ChannelService channelService,
                            ChannelGateway gateway,
                            SlackChannelBackend backend,
                            ChannelBindingStore channelBindingStore,
                            SlackThreadCacheStore threadCacheStore,
                            CredentialResolver credentialResolver) {
        this.bindingStore = bindingStore;
        this.channelService = channelService;
        this.gateway = gateway;
        this.backend = backend;
        this.channelBindingStore = channelBindingStore;
        this.threadCacheStore = threadCacheStore;
        this.credentialResolver = credentialResolver;
    }

    public SlackBindingDto put(UUID channelId, SlackBindingRequest req) {
        var channel = channelService.findById(channelId).orElse(null);
        if (channel == null) {
            throw new IllegalArgumentException("Channel not found: " + channelId);
        }
        if (channelBindingStore.findByChannelId(channelId).isPresent()) {
            throw new IllegalStateException("Channel already has a generic connector binding");
        }
        var creds = credentialResolver.resolve(req.workspaceId());
        String token = creds.get(CredentialPropertyKeys.BEARER_TOKEN);
        if (token == null) {
            throw new IllegalArgumentException("Missing credential: casehub.credentials." + req.workspaceId());
        }
        if (token.isBlank()) {
            throw new IllegalArgumentException("Credential casehub.credentials." + req.workspaceId() + " is configured but blank");
        }
        backend.evict(channelId);
        threadCacheStore.deleteAllByChannelId(channelId);

        SlackBotBinding binding = new SlackBotBinding();
        binding.channelId = channelId;
        binding.slackChannelId = req.slackChannelId();
        binding.workspaceId = req.workspaceId();
        binding.createdAt = Instant.now();
        bindingStore.save(binding);

        try {
            gateway.initChannel(channelId, new ChannelRef(channelId, channel.name()));
        } catch (DuplicateParticipatingBackendException e) {
            bindingStore.deleteByChannelId(channelId);
            throw new IllegalStateException("Channel already has a participating backend: " + e.getMessage());
        }

        return SlackBindingDto.from(channelId, binding);
    }

    public SlackBindingDto get(UUID channelId) {
        return bindingStore.findByChannelId(channelId)
                .map(b -> SlackBindingDto.from(channelId, b))
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException());
    }

    public void delete(UUID channelId) {
        backend.evict(channelId);
        gateway.deregisterBackend(channelId, SlackChannelBackend.BACKEND_ID);
        bindingStore.deleteByChannelId(channelId);
    }
}
