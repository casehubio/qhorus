package io.casehub.qhorus.runtime.cdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.message.EnforcementBlockedEvent;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.message.EnforcementExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
@Transactional
public class CdiEnforcementExecutor extends EnforcementExecutor {

    @Inject
    public CdiEnforcementExecutor(MessageDispatcher messageDispatcher,
                                  ChannelService channelService,
                                  CommitmentService commitmentService,
                                  Event<EnforcementBlockedEvent> enforcementBlockedEvent,
                                  ObjectMapper objectMapper) {
        super(messageDispatcher, channelService, commitmentService,
                e -> enforcementBlockedEvent.fireAsync(e), objectMapper);
    }

    CdiEnforcementExecutor() {}
}
