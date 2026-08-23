package io.casehub.qhorus.runtime.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.EnforcementMode;
import io.casehub.qhorus.api.message.EnforcementBlockedEvent;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
class EnforcementExecutor {

    private static final Logger LOG = Logger.getLogger(EnforcementExecutor.class);

    @Inject MessageDispatcher messageDispatcher;
    @Inject ChannelService channelService;
    @Inject CommitmentService commitmentService;
    @Inject Event<EnforcementBlockedEvent> enforcementBlockedEvent;
    @Inject ObjectMapper objectMapper;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void execute(Channel ch, MessageDispatch dispatch, List<TaggedAdvisory> violations,
                 String tenancyId) {
        List<String> violationMessages = violations.stream().map(TaggedAdvisory::message).toList();
        List<String> violationSources = violations.stream().map(TaggedAdvisory::source).distinct().toList();
        String action = ch.enforcementMode() == EnforcementMode.QUARANTINE ? "QUARANTINED" : "BLOCKED";

        ObjectNode telemetry = objectMapper.createObjectNode();
        telemetry.put("enforcement_action", action);
        ArrayNode violationsArray = telemetry.putArray("violations");
        violationMessages.forEach(violationsArray::add);
        ArrayNode sourcesArray = telemetry.putArray("violation_sources");
        violationSources.forEach(sourcesArray::add);
        telemetry.put("blocked_sender", dispatch.sender());
        telemetry.put("blocked_type", dispatch.type().name());
        telemetry.put("enforcement_mode", ch.enforcementMode().name());

        messageDispatcher.dispatch(MessageDispatch.builder()
                .channelId(ch.id())
                .sender("system:enforcement")
                .type(MessageType.EVENT)
                .telemetry(telemetry.toString())
                .actorType(ActorType.SYSTEM)
                .tenancyId(tenancyId)
                .build());

        if (ch.enforcementMode() == EnforcementMode.QUARANTINE) {
            channelService.pause(ch.id());
            commitmentService.expireByChannel(ch.id());
        }

        enforcementBlockedEvent.fireAsync(new EnforcementBlockedEvent(
                ch.id(), ch.name(), ch.enforcementMode(),
                dispatch.sender(), dispatch.type(),
                violationMessages, violationSources));
    }
}
