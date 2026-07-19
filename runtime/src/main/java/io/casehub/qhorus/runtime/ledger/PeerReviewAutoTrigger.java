package io.casehub.qhorus.runtime.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@UnlessBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true", enableIfMissing = true)
class PeerReviewAutoTrigger implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(PeerReviewAutoTrigger.class);

    @Inject
    MessageLedgerEntryRepository messageRepo;

    @Inject
    ReviewerResolver reviewerResolver;

    @Inject
    MessageDispatcher messageDispatcher;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (event.messageType() != MessageType.DONE) return;
        if (event.correlationId() == null) return;

        var entry = messageRepo.findEarliestWithSubjectByCorrelationId(
                event.correlationId(), event.tenancyId()).orElse(null);
        if (entry == null) return;
        if (!"COMMAND".equals(entry.messageType) && !"HANDOFF".equals(entry.messageType)) return;

        var reviewers = reviewerResolver.resolve(
                entry.channelId, List.of(), entry.id, event.tenancyId());
        if (reviewers.isEmpty()) return;

        for (String reviewerId : reviewers) {
            try {
                ObjectNode peerReview = objectMapper.createObjectNode();
                peerReview.put("ledger_entry_id", entry.id.toString());
                peerReview.put("original_command", entry.content);
                peerReview.put("completion_content", event.content());

                ObjectNode content = objectMapper.createObjectNode();
                content.set("peer_review", peerReview);

                messageDispatcher.dispatch(MessageDispatch.builder()
                        .channelId(entry.channelId)
                        .sender(entry.actorId)
                        .type(MessageType.QUERY)
                        .content(objectMapper.writeValueAsString(content))
                        .correlationId(UUID.randomUUID().toString())
                        .target(reviewerId)
                        .actorType(ActorType.SYSTEM)
                        .tenancyId(event.tenancyId())
                        .build());
            } catch (Exception e) {
                LOG.warnf(e, "Failed to send peer review QUERY to %s for entry %s",
                        reviewerId, entry.id);
            }
        }
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }
}
