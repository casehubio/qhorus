package io.casehub.qhorus.runtime.cdi;

import io.casehub.qhorus.api.store.ChannelBindingStore;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.runtime.QhorusEntityMapper;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.dashboard.QhorusDashboardService;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.message.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CdiQhorusDashboardService extends QhorusDashboardService {

    @Inject
    public CdiQhorusDashboardService(ChannelService channelService, InstanceService instanceService,
                                     MessageService messageService, MessageStore messageStore,
                                     QhorusEntityMapper entityMapper, ChannelBindingStore bindingStore) {
        super(channelService, instanceService, messageService, messageStore, entityMapper, bindingStore);
    }

    CdiQhorusDashboardService() {}
}
