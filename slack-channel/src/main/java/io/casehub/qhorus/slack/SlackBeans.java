package io.casehub.qhorus.slack;

import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.slack.core.SlackBindingCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class SlackBeans {

    @Produces
    @ApplicationScoped
    public SlackBindingCore slackBindingCore(SlackBotBindingStore bindingStore,
                                             ChannelService channelService,
                                             ChannelGateway gateway,
                                             SlackChannelBackend backend,
                                             ChannelBindingStore channelBindingStore,
                                             SlackThreadCacheStore threadCacheStore,
                                             CredentialResolver credentialResolver) {
        return new SlackBindingCore(bindingStore, channelService, gateway, backend,
                channelBindingStore, threadCacheStore, credentialResolver);
    }
}
