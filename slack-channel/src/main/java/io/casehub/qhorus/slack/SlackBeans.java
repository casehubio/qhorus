package io.casehub.qhorus.slack;

import io.casehub.connectors.slack.bot.SlackBotClient;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.slack.core.SlackBindingCore;
import io.casehub.qhorus.slack.core.SlackBotBindingStore;
import io.casehub.qhorus.slack.core.SlackChannelBackendCore;
import io.casehub.qhorus.slack.core.SlackInboundNormaliser;
import io.casehub.qhorus.slack.core.SlackThreadCacheStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SlackBeans {

    @Produces
    @ApplicationScoped
    public SlackInboundNormaliser slackInboundNormaliser() {
        return new SlackInboundNormaliser();
    }

    @Produces
    @ApplicationScoped
    public SlackChannelBackendCore slackChannelBackendCore(SlackBotBindingStore bindingStore,
                                                           SlackThreadCacheStore threadCacheStore,
                                                           SlackBotClient slackBotClient,
                                                           SlackInboundNormaliser slackInboundNormaliser,
                                                           ChannelGateway gateway,
                                                           CredentialResolver credentialResolver) {
        return new SlackChannelBackendCore(bindingStore, threadCacheStore, slackBotClient,
                slackInboundNormaliser, gateway, credentialResolver);
    }

    @Produces
    @ApplicationScoped
    public SlackBindingCore slackBindingCore(SlackBotBindingStore bindingStore,
                                             ChannelService channelService,
                                             ChannelGateway gateway,
                                             SlackChannelBackendCore backendCore,
                                             ChannelBindingStore channelBindingStore,
                                             SlackThreadCacheStore threadCacheStore,
                                             CredentialResolver credentialResolver) {
        return new SlackBindingCore(bindingStore, channelService, gateway, backendCore,
                channelBindingStore, threadCacheStore, credentialResolver);
    }
}
