package io.casehub.qhorus.runtime.cdi;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.event.ChannelMutationEvent;
import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.api.store.SpaceStore;
import io.casehub.qhorus.runtime.channel.SpaceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CdiSpaceService extends SpaceService {

    @Inject
    public CdiSpaceService(SpaceStore spaceStore,
                           ChannelStore channelStore,
                           CurrentPrincipal currentPrincipal,
                           Event<ChannelMutationEvent> mutationEvent) {
        super(spaceStore, channelStore, currentPrincipal, mutationEvent::fire);
    }

    CdiSpaceService() {}
}
