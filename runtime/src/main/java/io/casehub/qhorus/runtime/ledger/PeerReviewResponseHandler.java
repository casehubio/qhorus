package io.casehub.qhorus.runtime.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
@UnlessBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true", enableIfMissing = true)
class PeerReviewResponseHandler implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(PeerReviewResponseHandler.class);

    @Inject
    PeerAttestationWriter peerAttestationWriter;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (event.messageType() != MessageType.RESPONSE) return;
        if (event.content() == null) return;

        try {
            JsonNode root = objectMapper.readTree(event.content());
            JsonNode reviewResponse = root.get("peer_review_response");
            if (reviewResponse == null) return;

            JsonNode entryIdNode = reviewResponse.get("ledger_entry_id");
            JsonNode verdictNode = reviewResponse.get("verdict");
            if (entryIdNode == null || verdictNode == null) {
                LOG.warnf("peer_review_response missing ledger_entry_id or verdict on channel %s",
                        event.channelName());
                return;
            }

            UUID entryId = UUID.fromString(entryIdNode.asText());
            AttestationVerdict verdict = AttestationVerdict.valueOf(
                    verdictNode.asText().toUpperCase());
            String evidence = reviewResponse.has("evidence")
                    ? reviewResponse.get("evidence").asText() : null;

            peerAttestationWriter.write(entryId, verdict, evidence,
                    event.senderId(), event.tenancyId());
        } catch (Exception e) {
            LOG.warnf(e, "Could not parse peer_review_response from RESPONSE on channel %s — "
                    + "use attest() tool to record attestation manually", event.channelName());
        }
    }

    @Override
    public Scope scope() {
        return Scope.LOCAL;
    }
}
