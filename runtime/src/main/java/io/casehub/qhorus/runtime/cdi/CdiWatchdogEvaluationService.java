package io.casehub.qhorus.runtime.cdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.store.ChannelMembershipStore;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantCommitmentStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.CrossTenantWatchdogStore;
import io.casehub.qhorus.api.store.InstanceStore;
import io.casehub.qhorus.api.store.WatchdogStore;
import io.casehub.qhorus.api.watchdog.WatchdogAlertEvent;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.config.QhorusConfig;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.ledger.MessageLedgerEntryRepository;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.message.MessageService;
import io.casehub.qhorus.runtime.watchdog.WatchdogEvaluationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CdiWatchdogEvaluationService extends WatchdogEvaluationService {

    @Inject
    public CdiWatchdogEvaluationService(QhorusConfig config,
                                        MessageService messageService,
                                        WatchdogStore watchdogStore,
                                        CrossTenantChannelStore crossTenantChannelStore,
                                        CrossTenantMessageStore crossTenantMessageStore,
                                        CrossTenantCommitmentStore crossTenantCommitmentStore,
                                        CrossTenantWatchdogStore crossTenantWatchdogStore,
                                        InstanceStore instanceStore,
                                        Event<WatchdogAlertEvent> alertEvents,
                                        MessageLedgerEntryRepository messageRepo,
                                        ChannelMembershipStore channelMembershipStore,
                                        ChannelService channelService,
                                        InstanceService instanceService,
                                        CommitmentService commitmentService,
                                        ObjectMapper objectMapper) {
        super(config, messageService, watchdogStore, crossTenantChannelStore,
                crossTenantMessageStore, crossTenantCommitmentStore, crossTenantWatchdogStore,
                instanceStore, e -> alertEvents.fireAsync(e),
                (channelId, tenancyId) -> messageRepo.findLatestContextPressure(channelId, tenancyId)
                        .stream()
                        .map(entry -> new ContextPressureEntry(entry.actorId, entry.contextWindowPct))
                        .toList(),
                channelMembershipStore, channelService, instanceService, commitmentService, objectMapper);
    }

    CdiWatchdogEvaluationService() {}
}
