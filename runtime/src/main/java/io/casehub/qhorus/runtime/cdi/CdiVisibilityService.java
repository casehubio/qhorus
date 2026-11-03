package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.channel.ChannelMembershipService;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.channel.VisibilityService;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CdiVisibilityService extends VisibilityService {

    @Inject
    public CdiVisibilityService(ChannelService channelService,
                                 ChannelMembershipService membershipService,
                                 ChannelMembershipStore membershipStore,
                                 ChannelStore channelStore,
                                 CurrentPrincipal currentPrincipal) {
        super(channelService, membershipService, membershipStore, channelStore, currentPrincipal);
    }
}
